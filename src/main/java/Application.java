import ai.catboost.CatBoostError;
import ai.catboost.CatBoostModel;
import ai.catboost.CatBoostPredictions;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.smallrye.mutiny.Uni;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

@QuarkusMain
public class Application {
    private static final double THRESHOLD = 0.5;

    public static void main(String[] args) {
        if (args.length != 2) {
            Log.error("Invalid arguments");
            throw new RuntimeException();
        }

        List<TransactionModel> models = new ArrayList<>();
        String csvFile = args[0];
        CompletableFuture<List<TransactionModel>> modelsFuture = CompletableFuture.supplyAsync(() -> {

            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                reader.lines().map(line -> line.split(",")).skip(1).map(values -> {
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] == null) {
                            values[i] = "nan";
                        }
                    }
                    String amountRub = values[10];
                    String currency = values[11];
                    if (currency.equalsIgnoreCase("USD")) {
                        amountRub = String.valueOf(Integer.parseInt(amountRub) * 65);
                    } else if (currency.equalsIgnoreCase("EUR")) {
                        amountRub = String.valueOf(Integer.parseInt(amountRub) * 68);
                    } else {
                        amountRub = String.valueOf(Integer.parseInt(amountRub));
                    }
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    LocalDateTime eventTime = LocalDateTime.parse(values[0], formatter);
                    return new TransactionModel(eventTime, values[1], values[2],
                            values[4], values[5], values[6], values[7], values[8], values[9], values[10],
                            values[11], values[12], values[15], values[16], amountRub,
                            String.valueOf(eventTime.getHour()),
                            String.valueOf(eventTime.getMinute()),
                            String.valueOf(eventTime.getSecond()), null);
                }).forEachOrdered(models::add);
            } catch (IOException e) {
                Log.error("Error reading CSV file: " + e.getMessage());
                throw new RuntimeException();
            }
            return models;
        }, ForkJoinPool.commonPool());
        Uni.createFrom()
                .completionStage(modelsFuture)
                .await()
                .indefinitely();

        CompletableFuture.runAsync(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(args[1]))) {
                writer.write(Arrays.stream(TransactionModel.class.getDeclaredFields()).map(Field::getName).collect(Collectors.joining(",")));
                writer.newLine();
                ClassLoader classloader = Thread.currentThread().getContextClassLoader();
                InputStream is = classloader.getResourceAsStream("model.cbm");
                CatBoostModel catBoostModel = CatBoostModel.loadModel(is);
                for (TransactionModel model : models) {
                    CatBoostPredictions catBoostPredictions = catBoostModel.predict(new float[]{Float.parseFloat(model.amount_rub())},
                            new String[]{model.email(), model.ip(),
                                    model.cardToken(), model.paymentSystem(), model.providerId(),
                                    model.bankCountry(), model.partyId(), model.shopId(), model.currency(),
                                    model.bin_hash(), model.ms_pan_hash(), model.hours(), model.minutes(),
                                    model.seconds()});
                    writer.write(model.eventTime() + "," + model.email() + "," + model.ip() + "," +
                            model.cardToken() + "," + model.paymentSystem() + "," +
                            model.providerId() + "," + model.bankCountry() + "," + model.partyId() + "," +
                            model.shopId() + "," + model.amount() + "," +
                            model.currency() + "," + model.result() + "," +
                            model.bin_hash() + "," + model.ms_pan_hash() + "," + model.amount_rub() + "," +
                            model.hours() + "," + model.minutes() + "," + model.seconds() + "," +
                            (sigmoid(catBoostPredictions.get(0, 0)) > THRESHOLD ? "true" : "false"));
                    writer.newLine();
                }
                catBoostModel.close();
            } catch (IOException e) {
                Log.error("Error writing to CSV file: " + e.getMessage());
                throw new RuntimeException();
            } catch (CatBoostError e) {
                Log.error("Error during prediction " + e.getMessage());
                throw new RuntimeException();
            } finally {
                System.exit(0);
            }
        }, ForkJoinPool.commonPool());
        Uni.createFrom()
                .completionStage(modelsFuture)
                .await()
                .indefinitely();


        Quarkus.run(args);
    }

    private static double sigmoid(double x) {
        return 1. / (1 + Math.pow(Math.E, -x));
    }
}

import java.time.LocalDateTime;

public record TransactionModel(LocalDateTime eventTime, String email, String ip, String cardToken,
                               String paymentSystem, String providerId, String bankCountry,
                               String partyId, String shopId, String amount, String currency,
                               String result, String bin_hash,
                               String ms_pan_hash, String amount_rub, String hours, String minutes,
                               String seconds, String prediction) {
}

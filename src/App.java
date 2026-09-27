import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
    private record Transaction(long minute, long cents) {}

    public static List<String> process(List<String> records) {
        List<String> alerts = new ArrayList<>(List.of("card_id,event_id,minute,window_total_cents"));
        Map<String, String> seen = new HashMap<>();
        Map<String, Deque<Transaction>> windows = new HashMap<>();
        Map<String, Long> latestMinute = new HashMap<>();
        for (String record : records) {
            String[] row = record.split(",", -1);
            if (row.length != 4 || row[0].isBlank() || row[1].isBlank()) throw new IllegalArgumentException("Expected event_id,card_id,minute,amount_cents");
            long minute = Long.parseLong(row[2]);
            long cents = Long.parseLong(row[3]);
            if (minute < 0 || cents <= 0) throw new IllegalArgumentException("Minute and amount must be positive");
            String old = seen.putIfAbsent(row[0], record);
            if (old != null) {
                if (!old.equals(record)) throw new IllegalArgumentException("Conflicting duplicate event ID: " + row[0]);
                continue;
            }
            String card = row[1];
            if (minute < latestMinute.getOrDefault(card, -1L)) throw new IllegalArgumentException("Out-of-order card event");
            latestMinute.put(card, minute);
            Deque<Transaction> window = windows.computeIfAbsent(card, ignored -> new ArrayDeque<>());
            while (!window.isEmpty() && window.peekFirst().minute() < minute - 9) window.removeFirst();
            window.addLast(new Transaction(minute, cents));
            long total = 0;
            for (Transaction transaction : window) total = Math.addExact(total, transaction.cents());
            if (total >= 100_000) alerts.add(card + "," + row[0] + "," + minute + "," + total);
        }
        return alerts;
    }

    public static void main(String[] args) throws Exception {
        List<String> records = Files.readAllLines(Path.of("fixtures/events.csv"), StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).toList();
        if (args.length > 0) {
            if (!"--broker".equals(args[0]) || args.length < 2) throw new IllegalArgumentException("Usage: App [--broker bootstrap-server [topic-prefix]]");
            records = BrokerReplay.replay(records, args[1], args.length > 2 ? args[2] : "fraud-velocity");
        }
        for (String line : process(records)) System.out.println(line);
    }
}

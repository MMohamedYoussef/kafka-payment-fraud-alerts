import java.util.List;

public class AppTest {
    private static void same(List<String> expected, List<String> actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + " actual=" + actual);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid transaction was accepted");
    }

    public static void main(String[] args) {
        same(List.of("card_id,event_id,minute,window_total_cents", "C1,b,5,105000"),
                App.process(List.of("a,C1,1,60000", "b,C1,5,45000", "b,C1,5,45000", "c,C1,20,70000")));
        rejects(() -> App.process(List.of("bad,C1,2,-100")));
        rejects(() -> App.process(List.of("a,C1,2,100", "a,C1,2,200")));
        rejects(() -> App.process(List.of("a,C1,3,100", "b,C1,2,100")));
        same(List.of("card_id,event_id,minute,window_total_cents"), App.process(List.of()));
        System.out.println("fraud tests passed");
    }
}

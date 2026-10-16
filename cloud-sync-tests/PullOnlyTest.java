import com.osfans.trime.cloudsync.S3Client;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/** Run on a plain JDK. No real S3 endpoint or credentials are used. */
public class PullOnlyTest {
    private static final List<String> requests = new ArrayList<>();
    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    public void connect() { }
                    public void disconnect() { }
                    public boolean usingProxy() { return false; }
                    public int getResponseCode() { requests.add(getRequestMethod()); return 200; }
                    public InputStream getInputStream() { return new ByteArrayInputStream("verified-download".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                    public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
                };
            }
        } : null);
        File cache = Files.createTempDirectory("pull-only-test").toFile();
        try {
            S3Client reader = new S3Client("test-access", "test-secret", cache, true);
            try (S3Client.Reply reply = reader.get("current.json", 100)) {
                require(Files.readString(reply.file.toPath()).equals("verified-download"));
                require(reply.etag == null); // Pulling must work without a write-version ETag.
            }
            for (String target : Arrays.asList("objects/test", "current.json")) {
                try { reader.put(target, null, null, null); throw new AssertionError("PUT allowed"); }
                catch (IllegalStateException expected) { require(expected.getMessage().contains("仅拉取")); }
                try { reader.delete(target); throw new AssertionError("DELETE allowed"); }
                catch (IllegalStateException expected) { require(expected.getMessage().contains("仅拉取")); }
            }
            require(requests.equals(Arrays.asList("GET")));
            S3Client writer = new S3Client("test-access", "test-secret", cache, false);
            File payload = new File(cache, "payload");
            Files.writeString(payload.toPath(), "test-payload");
            writer.put("objects/test", payload, null, null);
            writer.put("current.json", payload, "If-Match", "test-etag");
            require(requests.equals(Arrays.asList("GET", "PUT", "PUT")));
            java.util.concurrent.atomic.AtomicBoolean changedMode = new java.util.concurrent.atomic.AtomicBoolean(false);
            S3Client activeWorker = new S3Client("test-access", "test-secret", cache, changedMode::get);
            changedMode.set(true);
            try { activeWorker.put("current.json", payload, "If-Match", "test-etag"); throw new AssertionError("Worker ignored mode change"); }
            catch (IllegalStateException expected) { require(requests.size() == 3); }
            System.out.println("PASS: read-only GET succeeds, all PUT/DELETE blocked before network, explicit upload mode retains object and conditional manifest writes");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(cache.toPath())) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.delete(path); } catch (IOException e) { throw new UncheckedIOException(e); } });
            }
        }
    }
}

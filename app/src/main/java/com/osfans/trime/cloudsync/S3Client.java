// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class S3Client {
    private static final String HOST = "s3.loto2.top";
    private final String access, secret;
    private final File cache;
    private final java.util.function.BooleanSupplier readOnly;
    public S3Client(String access, String secret, File cache) {
        this(access, secret, cache, false);
    }
    public S3Client(String access, String secret, File cache, boolean readOnly) {
        this(access, secret, cache, () -> readOnly);
    }
    public S3Client(String access, String secret, File cache, java.util.function.BooleanSupplier readOnly) {
        this.access = access; this.secret = secret; this.cache = cache;
        this.readOnly = readOnly;
        if (!cache.exists() && !cache.mkdirs()) throw new IllegalStateException("无法建立缓存目录");
    }
    public static class HttpError extends IOException {
        public final int status;
        HttpError(int status) { super("S3 HTTP " + status); this.status = status; }
    }
    public static class Reply implements AutoCloseable {
        public final File file; public final String etag;
        Reply(File f, String e) { file = f; etag = e; }
        public void close() { file.delete(); }
    }
    public Reply get(String key, long limit) throws Exception { return request("GET",key,null,null,null,limit); }
    public void put(String key, File file, String condition, String value) throws Exception {
        try (Reply r = request("PUT",key,file,condition,value,4096)) { }
    }
    public void delete(String key) throws Exception { try (Reply r = request("DELETE",key,null,null,null,4096)) { } }
    public static String hash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) { byte[] b = new byte[65536]; int n; while ((n=in.read(b))!=-1) md.update(b,0,n); }
        return hex(md.digest());
    }
    public static String hex(byte[] b) { StringBuilder s=new StringBuilder(); for(byte x:b) s.append(String.format(Locale.US,"%02x",x&255)); return s.toString(); }
    private static byte[] hmac(byte[] k,String v) throws Exception { Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(k,"HmacSHA256"));return m.doFinal(v.getBytes(StandardCharsets.UTF_8)); }
    private static String hashText(String s) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private static String encode(String s) { StringBuilder out=new StringBuilder();for(byte b:s.getBytes(StandardCharsets.UTF_8)){int c=b&255;if(c=='/'||c=='-'||c=='_'||c=='.'||c=='~'||c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9')out.append((char)c);else out.append(String.format(Locale.US,"%%%02X",c));}return out.toString(); }
    private Reply request(String method,String key,File body,String condition,String value,long limit) throws Exception {
        if (!"GET".equals(method) && readOnly.getAsBoolean()) throw new IllegalStateException("仅拉取模式禁止写入云端");
        String stamp=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'",Locale.US).withZone(ZoneOffset.UTC).format(Instant.now());
        String date=stamp.substring(0,8), scope=date+"/us-east-1/s3/aws4_request";
        String path=encode("/sync/rime/v1/"+key);
        String payload=body==null?hashText(""):hash(body);
        TreeMap<String,String> headers=new TreeMap<>();
        headers.put("host",HOST); headers.put("x-amz-content-sha256",payload);headers.put("x-amz-date",stamp);
        if(condition!=null) headers.put(condition.toLowerCase(Locale.US),value);
        StringBuilder canonicalHeaders=new StringBuilder();for(Map.Entry<String,String> e:headers.entrySet()) canonicalHeaders.append(e.getKey()).append(':').append(e.getValue().trim()).append('\n');
        String signed=String.join(";",headers.keySet());
        String canonical=method+"\n"+path+"\n\n"+canonicalHeaders+"\n"+signed+"\n"+payload;
        byte[] signing=hmac(hmac(hmac(hmac(("AWS4"+secret).getBytes(StandardCharsets.UTF_8),date),"us-east-1"),"s3"),"aws4_request");
        String signature=hex(hmac(signing,"AWS4-HMAC-SHA256\n"+stamp+"\n"+scope+"\n"+hashText(canonical)));
        HttpURLConnection conn=(HttpURLConnection)new URL("https://"+HOST+path).openConnection();
        conn.setInstanceFollowRedirects(false);conn.setRequestMethod(method);conn.setConnectTimeout(30000);conn.setReadTimeout(180000);
        for(Map.Entry<String,String> e:headers.entrySet())conn.setRequestProperty(e.getKey(),e.getValue());
        conn.setRequestProperty("Authorization","AWS4-HMAC-SHA256 Credential="+access+"/"+scope+", SignedHeaders="+signed+", Signature="+signature);
        File received=null;
        try {
            if(body!=null){conn.setDoOutput(true);conn.setFixedLengthStreamingMode(body.length());try(InputStream in=new FileInputStream(body);OutputStream out=conn.getOutputStream()){copy(in,out,Long.MAX_VALUE);}}
            int status=conn.getResponseCode();if(status<200||status>=300)throw new HttpError(status);
            if(conn.getContentLengthLong()>limit)throw new IOException("云端文件超过大小限制");
            received=File.createTempFile("s3-",".tmp",cache);
            try(InputStream in=conn.getInputStream();OutputStream out=new FileOutputStream(received)){copy(in,out,limit);}
            return new Reply(received,conn.getHeaderField("ETag"));
        }catch(Exception e){if(received!=null)received.delete();throw e;}finally{conn.disconnect();}
    }
    public static long copy(InputStream in,OutputStream out,long limit) throws IOException {byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("文件超过大小限制");out.write(b,0,n);}return total;}
}

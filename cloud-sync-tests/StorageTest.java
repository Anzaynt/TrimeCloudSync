import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.osfans.trime.cloudsync.SyncStorage;
public class StorageTest {
 static void require(boolean result) { if (!result) throw new AssertionError(); }
 public static void main(String[] args) throws Exception {
  File root=Files.createTempDirectory("rime-storage-test-").toFile();
  File cache=new File(root,"objects"),backup=new File(root,"backups"),peers=new File(root,"sync");
  cache.mkdirs();backup.mkdirs();peers.mkdirs();
  new File(cache,"unrelated.txt").createNewFile();
  for(int i=0;i<100;i++) {
   String hash=String.format("%064x",i);
   Files.write(new File(cache,hash).toPath(),new byte[4096]);
   new File(cache,"s3-crash.tmp").createNewFile();new File(cache,"capture-crash.txt").createNewFile();
   SyncStorage.pruneObjects(cache,Collections.singleton(hash));
   require(cache.list().length==2);require(new File(cache,hash).exists());
   File generation=new File(backup,String.valueOf(1000000000000L+i));generation.mkdirs();
   Files.write(new File(generation,"schema.yaml").toPath(),new byte[2048]);
   SyncStorage.pruneBackups(backup);require(backup.list().length<=2);
  }
  File oversized=new File(backup,"9999999999999");oversized.mkdirs();
  try(RandomAccessFile f=new RandomAccessFile(new File(oversized,"huge"),"rw")){f.setLength(129L*1024*1024);}
  SyncStorage.pruneBackups(backup);require(!oversized.exists());
  for(String name:Arrays.asList("cloud-own","cloud-current","cloud-obsolete","desktop-peer")) {
   File f=new File(peers,name);f.mkdirs();new File(f,"words.userdb.txt").createNewFile();
  }
  SyncStorage.prunePeers(peers,"cloud-own","cloud-current");
  require(new File(peers,"cloud-own/words.userdb.txt").exists());
  require(new File(peers,"cloud-current/words.userdb.txt").exists());
  require(new File(peers,"desktop-peer/words.userdb.txt").exists());
  require(!new File(peers,"cloud-obsolete").exists());
  Files.walk(root.toPath()).sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}});
  System.out.println("PASS: 100 sync generations, interrupted request cleanup, backup count/128MiB cap, own/current/non-cloud peer preservation");
 }
}

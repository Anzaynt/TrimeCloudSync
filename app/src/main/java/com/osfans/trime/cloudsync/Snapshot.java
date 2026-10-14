// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Snapshot {
    public final String userId, dbName;
    public final Set<String> entries;
    public Snapshot(File file) throws IOException {
        String user=null,db=null;Set<String> keys=new HashSet<>();
        if(file.length()>32L*1024*1024)throw new IOException("词库快照过大");
        try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){String s;while((s=r.readLine())!=null){
            if(s.startsWith("#@/user_id\t"))user=s.substring(11).trim();
            else if(s.startsWith("#@/db_name\t"))db=s.substring(11).trim();
            else if(!s.startsWith("#")&&!s.isEmpty()){String[] fields=s.split("\t",-1);if(fields.length!=3)throw new IOException("无法识别词库快照记录");keys.add(fields[0]+"\t"+fields[1]);}
        }}
        if(user==null||db==null||!user.matches("[a-zA-Z0-9_-]+")||!db.matches("[a-zA-Z0-9_-]+"))throw new IOException("快照设备 ID 或词库名无效");
        userId=user;dbName=db;entries=keys;
    }
    public void requireMerged(Snapshot cloud) throws IOException {
        if(!dbName.equals(cloud.dbName))throw new IOException("词库名称不一致");
        if(!entries.containsAll(cloud.entries))throw new IOException("本机快照尚未包含全部云端词条；请先在同文中同步用户资料");
    }
}

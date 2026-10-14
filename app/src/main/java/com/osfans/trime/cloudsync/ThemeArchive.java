// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.cloudsync;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;

public final class ThemeArchive {
    public static String repository(String input) throws Exception {
        URI uri = new URI(input.trim());
        if (!"https".equals(uri.getScheme()) || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new IOException("请输入 https://github.com/作者/仓库 的公开仓库根网址");
        String path=uri.getRawPath().replaceAll("/$", "");
        if (!path.matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) throw new IOException("目前支持仓库根网址，不支持 tree/blob 子页面");
        String repo=path.substring(1).replaceAll("\\.git$", "");
        if (Arrays.asList(repo.split("/")).contains("..") || Arrays.asList(repo.split("/")).contains(".")) throw new IOException("无效仓库名");
        return repo;
    }
    public static String destination(String path) throws IOException {
        if (path.startsWith("/") || path.contains("\\") || path.contains(":") || path.indexOf('\0')>=0
                || Arrays.stream(path.split("/",-1)).anyMatch(p->p.equals("..") || p.equals(".") || p.isEmpty())) throw new IOException("主题包包含不安全路径");
        String[] parts=path.split("/");
        for(int i=0;i<parts.length-1;i++) if(parts[i].equals("fonts") || parts[i].equals("backgrounds")) {
            String tail=String.join("/",Arrays.copyOfRange(parts,i,parts.length));
            String lower=tail.toLowerCase(Locale.ROOT);
            if(parts[i].equals("fonts") && lower.matches(".*\\.(ttf|otf|ttc)")) return tail;
            if(parts[i].equals("backgrounds") && lower.matches(".*\\.(png|jpg|jpeg|webp|gif)")) return tail;
            return null;
        }
        String name=parts[parts.length-1];
        if(name.endsWith(".custom.yaml") && !name.endsWith(".trime.custom.yaml")) return null;
        if(!name.endsWith(".yaml") || name.endsWith(".schema.yaml") || name.endsWith(".dict.yaml")) return null;
        if(Arrays.asList("installation.yaml","user.yaml","default.yaml","default.custom.yaml","weasel.yaml","weasel.custom.yaml","squirrel.yaml","squirrel.custom.yaml","trime.yaml","trime.custom.yaml").contains(name)) return null;
        return name;
    }
    public static List<String> extract(File archive,File target) throws IOException {
        Set<String> names=new LinkedHashSet<>();long total=0;int count=0;
        try(ZipInputStream zip=new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;byte[] buffer=new byte[65536];
            while((entry=zip.getNextEntry())!=null) {
                if(++count>4096) throw new IOException("主题包文件过多");
                String path=entry.getName();
                if(entry.isDirectory()) { destination(path.replaceAll("/$",""));continue; }
                String relative=destination(path);
                if(relative!=null && !names.add(relative)) throw new IOException("主题包包含重名文件："+relative);
                File output=relative==null?null:new File(target,relative);
                if(output!=null){if(!output.getCanonicalPath().startsWith(target.getCanonicalPath()+File.separator))throw new IOException("路径越界");output.getParentFile().mkdirs();}
                try(OutputStream out=output==null?new OutputStream(){ public void write(int value){} public void write(byte[] data,int offset,int length){} }:new FileOutputStream(output)) {
                    long size=0;int n;
                    while((n=zip.read(buffer))!=-1){size+=n;total+=n;if(size>32L*1024*1024 || total>128L*1024*1024)throw new IOException("主题包超过容量上限");out.write(buffer,0,n);}
                }
            }
        }
        if(names.stream().noneMatch(n->n.endsWith(".trime.yaml"))) throw new IOException("仓库没有可导入的 .trime.yaml 主题");
        return new ArrayList<>(names);
    }
}

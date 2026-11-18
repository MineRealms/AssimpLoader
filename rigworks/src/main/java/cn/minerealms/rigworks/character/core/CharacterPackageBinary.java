package cn.minerealms.rigworks.character.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Compressed cache/transport container. Compression provides no secrecy or trust. */
public final class CharacterPackageBinary {
    public static final int MAX_BYTES=CharacterVisualPackage.MAX_BYTES+1024;
    private static final int MAGIC=0x5257504b,VERSION=1;
    private CharacterPackageBinary(){}
    public static byte[] encode(CharacterVisualPackage bundle)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream header=new DataOutputStream(bytes)) {
            header.writeInt(MAGIC);header.writeInt(VERSION);
            try(GZIPOutputStream gzip=new GZIPOutputStream(header)){gzip.write(bundle.json().getBytes(StandardCharsets.UTF_8));}
        }
        byte[] result=bytes.toByteArray();if(result.length>MAX_BYTES)throw new IOException("Compressed character package exceeds its budget");return result;
    }
    public static CharacterVisualPackage decode(byte[] bytes)throws IOException {
        if(bytes.length<10||bytes.length>MAX_BYTES)throw new IOException("Invalid compressed character package length");
        try(DataInputStream header=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(header.readInt()!=MAGIC||header.readInt()!=VERSION)throw new IOException("Unknown character package binary schema");
            try(GZIPInputStream gzip=new GZIPInputStream(header)) {
                byte[] source=gzip.readNBytes(CharacterVisualPackage.MAX_BYTES+1);
                if(source.length>CharacterVisualPackage.MAX_BYTES)throw new IOException("Expanded character package exceeds 512 KiB");
                String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(source)).toString();
                return CharacterVisualPackage.parse(json);
            }
        }
    }
}

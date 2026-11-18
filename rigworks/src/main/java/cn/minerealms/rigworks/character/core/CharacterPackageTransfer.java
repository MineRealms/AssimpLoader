package cn.minerealms.rigworks.character.core;

import java.io.ByteArrayOutputStream;

/** Ordered, bounded assembler shared by uploads and downloads. */
public final class CharacterPackageTransfer {
    public static final int CHUNK_BYTES=16384;
    private final String hash;
    private final int size;
    private final ByteArrayOutputStream bytes;
    private int next;
    public CharacterPackageTransfer(String hash,int size) {
        if(hash==null||!hash.matches("[a-f0-9]{64}")||size<2||size>CharacterPackageBinary.MAX_BYTES)
            throw new IllegalArgumentException("Invalid package transfer bounds");
        this.hash=hash;this.size=size;bytes=new ByteArrayOutputStream(size);
    }
    public boolean append(int index,byte[] chunk) {
        int expected=Math.min(CHUNK_BYTES,size-bytes.size());
        if(index!=next||chunk==null||chunk.length!=expected||expected==0)throw new IllegalArgumentException("Out-of-order or oversized package chunk");
        bytes.writeBytes(chunk);next++;return bytes.size()==size;
    }
    public byte[] finish() {
        if(bytes.size()!=size)throw new IllegalStateException("Incomplete package transfer");
        byte[] result=bytes.toByteArray();
        if(!CharacterVisualConfig.hash(result).equals(hash))throw new IllegalArgumentException("Package SHA-256 mismatch");
        return result;
    }
}

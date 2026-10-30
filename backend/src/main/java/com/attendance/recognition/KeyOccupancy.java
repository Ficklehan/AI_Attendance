package com.attendance.recognition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 并行 vision 占用的 keyIndex，acquire 时排除 in-flight，避免 8 路挤一把。
 */
public final class KeyOccupancy {

    private final Set<Integer> inUse = ConcurrentHashMap.newKeySet();

    public Set<Integer> snapshot() {
        return Collections.unmodifiableSet(new HashSet<Integer>(inUse));
    }

    public boolean tryOccupy(int keyIndex) {
        return inUse.add(Integer.valueOf(keyIndex));
    }

    public void release(int keyIndex) {
        inUse.remove(Integer.valueOf(keyIndex));
    }

    public int size() {
        return inUse.size();
    }
}

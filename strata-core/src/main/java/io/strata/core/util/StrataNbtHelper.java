package io.strata.core.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class StrataNbtHelper {

    private StrataNbtHelper() {}

    public static void putIdentifier(NbtCompound nbt, String key, Identifier id) {
        nbt.putString(key, id.toString());
    }

    public static Identifier getIdentifier(NbtCompound nbt, String key) {
        return Identifier.of(nbt.getString(key, ""));
    }

    public static void putStringList(NbtCompound nbt, String key, List<String> list) {
        NbtList nbtList = new NbtList();
        for (String s : list) {
            nbtList.add(NbtString.of(s));
        }
        nbt.put(key, nbtList);
    }

    public static List<String> getStringList(NbtCompound nbt, String key) {
        NbtList nbtList = nbt.getListOrEmpty(key);
        List<String> result = new ArrayList<>(nbtList.size());
        for (int i = 0; i < nbtList.size(); i++) {
            result.add(nbtList.getString(i, ""));
        }
        return result;
    }
}

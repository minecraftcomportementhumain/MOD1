package com.example.mysubmod.submodes.submode1.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CandyFileListManager {
    private static CandyFileListManager instance;
    private List<String> availableFiles = new ArrayList<>();
    private boolean hasFileList = false;

    private CandyFileListManager() {}

    public static CandyFileListManager getInstance() {
        if (instance == null) {
            instance = new CandyFileListManager();
        }
        return instance;
    }

    public void setFileList(List<String> files) {
        this.availableFiles = new ArrayList<>(files);
        this.hasFileList = !files.isEmpty();
    }

    public List<String> getFileList() {
        return new ArrayList<>(availableFiles);
    }

    public boolean hasFileList() {
        return hasFileList;
    }

    public void clear() {
        this.availableFiles.clear();
        this.hasFileList = false;
    }
}

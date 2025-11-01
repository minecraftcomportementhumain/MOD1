package com.example.mysubmod.submodes.submodeParent.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class FileListManager {
    private static FileListManager instance;
    private List<String> availableFiles = new ArrayList<>();
    private boolean hasFileList = false;

    private FileListManager() {}

    public static FileListManager getInstance() {
        if (instance == null) {
            instance = new FileListManager();
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

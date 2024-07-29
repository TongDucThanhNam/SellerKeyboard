package com.example.sellerkeyboard;

import java.io.File;

public class ButtonData {
    private String name;
    private String MIMEType;
    private File file;
    private String content;

    public ButtonData(String name, String MIMEType, File file) {
        this.name = name;
        this.MIMEType = MIMEType;
        this.file = file;
        content = "";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMIMEType() {
        return MIMEType;
    }

    public void setMIMEType(String MIMEType) {
        this.MIMEType = MIMEType;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}

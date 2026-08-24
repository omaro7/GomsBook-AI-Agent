/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.io.File;

public class EpubImage
{
    private final File file;    // 이 <img>가 속한 XHTML 파일
    private final int index;    // 해당 파일 내 img 인덱스 (0,1,2...)
    private final String src;   // img의 src (표시는 read-only로 둘 예정)
    private String imgId;     	// chapter20-1_img_1
    private String alt;         // 편집 가능
    private String role;        // 편집 가능
    private String ariaHidden;  // 편집 가능 ("true"/"false" 또는 "")

    public EpubImage(File file, int index, String src, String imgId, String alt, String role, String ariaHidden) {
        this.file = file;
        this.index = index;
        this.src = src;
        this.imgId = imgId;
        this.alt = alt;
        this.role = role;
        this.ariaHidden = ariaHidden;
    }

    public File getFile() { return file; }
    
    public int getIndex() { return index; }
    
    public String getSrc() { return src; }

    public String getImgId() { return imgId; }
    
    public void setImgId(String imgId) { this.imgId = imgId; }

    public String getAlt() { return alt; }
    
    public void setAlt(String alt) { this.alt = alt; }

    public String getRole() { return role; }
    
    public void setRole(String role) { this.role = role; }

    public String getAriaHidden() { return ariaHidden; }
    
    public void setAriaHidden(String ariaHidden) { this.ariaHidden = ariaHidden; }
}

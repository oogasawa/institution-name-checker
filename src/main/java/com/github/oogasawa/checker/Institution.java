package com.github.oogasawa.checker;

import io.quarkus.qute.TemplateData;

@TemplateData
public class Institution {
    public String kakenhiCode;
    public String nameJa;
    public String url;
    public String nameEn;

    public Institution(String kakenhiCode, String nameJa, String url, String nameEn) {
        this.kakenhiCode = kakenhiCode;
        this.nameJa = nameJa;
        this.url = url;
        this.nameEn = nameEn;
    }
}

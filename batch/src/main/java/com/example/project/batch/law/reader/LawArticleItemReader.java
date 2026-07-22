package com.example.project.batch.law.reader;

import com.example.project.batch.law.dto.LawArticle;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.support.ListItemReader;

import java.time.LocalDate;
import java.util.List;

public class LawArticleItemReader {

    public static ItemReader<LawArticle> mock() {
        LawArticle a = new LawArticle();
        a.setLawName("상속세 및 증여세법");
        a.setArticleNo("제53조");
        a.setTitle("증여재산 공제");
        a.setContent("거주자가 증여를 받은 경우 ...");
        a.setEffectiveDate(LocalDate.now());

        return new ListItemReader<>(List.of(a));
    }
}

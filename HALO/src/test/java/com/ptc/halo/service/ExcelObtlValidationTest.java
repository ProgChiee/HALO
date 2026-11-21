package com.ptc.halo.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcelObtlValidationTest {
    private final ExcelObtl.Row section=new ExcelObtl.Row(6,List.of("S","1","CHAPTER","2","8","","1","Hospitality","Weeks 2–8","","Complete","Pending professor review"));
    private Map<String,List<ExcelObtl.Row>> fixture(){
        var map=new HashMap<String,List<ExcelObtl.Row>>();
        map.put("START_HERE",List.of(new ExcelObtl.Row(2,List.of("VERSION 1.0"))));
        map.put("COURSE_INFO",List.of(new ExcelObtl.Row(8,List.of("","Course Title *","Course")),new ExcelObtl.Row(18,List.of("","Course Description *","Description"))));
        for(String sheet:List.of("PILO","CILO"))map.put(sheet,List.of(header(ExcelObtl.OUTCOME),new ExcelObtl.Row(6,List.of("1","Outcome"))));
        map.put("SECTIONS",List.of(header(ExcelObtl.SECTION),section));
        map.put("CONTENT",List.of(header(ExcelObtl.CONTENT),new ExcelObtl.Row(6,List.of("S","1","Topics"))));
        return map;
    }
    private ExcelObtl.Row header(String text){return new ExcelObtl.Row(5,List.of(text.split("\\|")));}
    @Test void optionalSheetsAndSharedSchedule(){assertEquals("Weeks 2–8",ExcelObtl.validate(fixture()).sections().get(0).definition().at(8));}
    @Test void missingSheet(){var m=fixture();m.remove("PILO");assertThrows(ResponseStatusException.class,()->ExcelObtl.validate(m));}
    @Test void duplicateSection(){var m=fixture();m.put("SECTIONS",List.of(header(ExcelObtl.SECTION),section,section));assertThrows(ResponseStatusException.class,()->ExcelObtl.validate(m));}
    @Test void malformedRange(){var m=fixture();var cells=new ArrayList<>(section.cells());cells.set(3,"9");m.put("SECTIONS",List.of(header(ExcelObtl.SECTION),new ExcelObtl.Row(6,cells)));assertThrows(ResponseStatusException.class,()->ExcelObtl.validate(m));}
    @Test void orphanContent(){var m=fixture();m.put("CONTENT",List.of(header(ExcelObtl.CONTENT),new ExcelObtl.Row(6,List.of("OTHER","1","Topic"))));assertThrows(ResponseStatusException.class,()->ExcelObtl.validate(m));}
    @Test void corruptPackage(){assertThrows(ResponseStatusException.class,()->ExcelObtl.parse(new byte[]{42}));}
}

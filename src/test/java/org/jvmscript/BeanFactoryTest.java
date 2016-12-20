package org.jvmscript;

import org.jvmscript.record.DelimitedRecordFactory;

import java.util.ArrayList;

public class BeanFactoryTest {

    public static void main(String[] args) throws Exception {
        DelimitedRecordFactory factory = new DelimitedRecordFactory();
        factory.setHeaderRows(1);
        factory.setTrailerRows(1);

        //ArrayList<Sec> actList = factory.getRecordListByPositionFromFile("input/KIRK_SEC", Sec.class);
//        System.out.println("SEC List Size = " + actList.size());
//        factory.writeRecordListToDelimitedFile("input/NEW_KIRK_SEC", actList);
    }
}

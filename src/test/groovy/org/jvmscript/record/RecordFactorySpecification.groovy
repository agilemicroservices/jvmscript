package org.jvmscript.record

import spock.lang.Specification

class RecordFactorySpecification extends Specification {

    class RecordTest {
        @DataField(id = 0, name = "fieldOne")       public String fieldOne;
        @DataField(id = 2, name = "fieldThree")     public BigDecimal fieldThree;
        @DataField(id = 1, name = "fieldTwo")       public Integer fieldTwo;
    }

  DelimitedRecordFactory recordFactory;

    def setup() {
        recordFactory = new DelimitedRecordFactory()

    }

    def "Testing getIdDataFieldMapByClass"() {
        when:
            def fieldMap = recordFactory.getIdDataFieldMapByClass(RecordTest.class)
        then:
            def beanField = fieldMap.get(1)
            beanField.dataField.id() == 1
            beanField.dataField.name() == 'fieldTwo'
            fieldMap.size() == 3
    }

    def "Testing setBeanField"() {
        when:
        def fieldMap = recordFactory.getIdDataFieldMapByClass(RecordTest.class)
        def bigDecimalBeanField = fieldMap.get(2)
        def bean = new RecordTest()
        def stringValue = "12,000.50-"
        recordFactory.setBeanField(bean, bigDecimalBeanField,stringValue)

        then:
        bean.fieldThree == -12000.50

    }


}

package org.jvmscript.record

import spock.lang.Specification

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class FixedWidthRecordFactorySpecification extends Specification {

    class FixedWidthTestBean {
        @FixedWidthField(name = "Date", start = 1, length = 8, dateFormat = "yyyyMMdd")             public LocalDate date;
        @FixedWidthField(name = "Date Time", start = 9, length = 11, dateFormat = "yyyyMMdd-hh")    public LocalDateTime dateTime;
        @FixedWidthField(name = "BigDecimal", start = 20, length = 5, scale = 2)          public BigDecimal bigDecimal = new BigDecimal("100.0095");
        @FixedWidthField(name = "Long", start = 25, length = 6)                                     public Long longValue = new Long("99999");
        @FixedWidthField(name = "Integer", start = 31, length = 5)                                  public Integer integerValue = new Integer("1111");
        @FixedWidthField(name = "Today String", start = 35, length = 10)                            public String todayString = "TODAY";
        @FixedWidthField(name = "End String", start = 45, length = 7)                               public String endString = "!!END!!";    }

    FixedWidthRecordFactory fixedWidthRecordFactory
    FixedWidthTestBean fixedWidthTestBean;

    def setup() {
        fixedWidthRecordFactory = new FixedWidthRecordFactory()
        fixedWidthTestBean = new FixedWidthTestBean()
    }

    def "Testing  BeanFactory.convertLocalDateTimeToFixedWidth()"() {
        when:
            fixedWidthTestBean.dateTime = LocalDateTime.of(LocalDate.of(2016,01, 01), LocalTime.MIDNIGHT);
            String dateString = fixedWidthRecordFactory.convertLocalDateTimeToFixedWidth(fixedWidthTestBean.dateTime, "name",  "yyyyMMdd-HH", 14)

        then:
            dateString == '20160101-00   '
    }

    def "Testing  BeanFactory.convertLocalDateTimeToFixedWidthException()"() {
        when:
        fixedWidthTestBean.dateTime = LocalDateTime.of(LocalDate.of(2016,01, 01), LocalTime.MIDNIGHT);
        String dateString = fixedWidthRecordFactory.convertLocalDateTimeToFixedWidth(fixedWidthTestBean.dateTime, "name",  "yyyyMMdd-HH", 4)

        then:
            thrown Exception
    }


    def "Testing  BeanFactory.convertBigDecimalToFixedWidth()"() {
        //convertBigDecimalToFixedWidth(BigDecimal bigDecimal, String name, int impliedDecimal, int maxLength)

        when:
        BigDecimal testValue = new BigDecimal("1234.016")
        String bigDecimalString = fixedWidthRecordFactory.convertBigDecimalToFixedWidth(testValue, "name",  2, 14)

        then:
        bigDecimalString == '00000000123402'
    }

    def "Testing  BeanFactory.convertLongToFixedWidth"() {

        when:
        Long testValue = new Long("1234")
        String stringValue = fixedWidthRecordFactory.convertLongToFixedWidth(testValue, "name",  6)

        then:
        stringValue == '001234'
    }

    def "Testing  BeanFactory.convertObjectToFixedWidth"() {

        when:
        String testValue = "TODAY"
        String stringValue = fixedWidthRecordFactory.convertObjectToFixedWidth(testValue, "name",  6)

        then:
        stringValue == 'TODAY '
    }

    def "Testing  BeanFactory.convertObjectToFixedWidthException"() {

        when:
        String testValue = "TODAY"
        String stringValue = fixedWidthRecordFactory.convertObjectToFixedWidth(testValue, "name",  4)

        then:
        thrown Exception
    }

    def "Testing  BeanFactory.buildStringBufferFromFixedWidthBeanFieldList"() {

        when:
        def bean = new FixedWidthTestBean()
        bean.dateTime = LocalDateTime.of(LocalDate.of(2016,01, 01), LocalTime.MIDNIGHT)
        bean.date = LocalDate.of(2016,01, 01)
        def fieldList = fixedWidthRecordFactory.getFixedWidthDataFieldMapByClass(bean.getClass())
        String stringValue = fixedWidthRecordFactory.buildStringBufferFromFixedWidthBeanFieldList(bean, fieldList)

        then:
        stringValue == '2016010120160101-12100010999990111TODAY     !!END!!'
    }

//    def "Testing  BeanFactory.writeFixedWidthBeanListToFile"() {
//
//        when:
//        def bean = new FixedWidthTestBean()
//        bean.dateTime = LocalDateTime.of(LocalDate.of(2016,01, 01), LocalTime.MIDNIGHT)
//        bean.date = LocalDate.of(2016,01, 01)
//        def beanList = new ArrayList<FixedWidthTestBean>()
//        beanList.add(bean)
//        String stringValue = fixedWidthRecordFactory.writeFixedWidthBeanListToFile('/dev/fixed_length.txt', beanList)
//
//        then:
//        println 'sucess'
//    }

}

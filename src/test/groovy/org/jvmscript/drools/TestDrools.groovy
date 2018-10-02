//package org.jvmscript.drools
//
//import org.junit.Test
//import spock.lang.Specification
//
// class TestDrools extends Specification{
//    def "Testing Simple Rule"() {
//        when:
//            def droolsUtility = new DroolsUtility()
//            def rule1 = """
//                rule "HelloWorld"
//                    when
//                        str : String()
//                    then
//                        System.out.println("Hello World String = " + str);
//                end
//            """
//
//            def rule2 = """
//                rule "HelloWorld2"
//                    when
//                        str : String()
//                    then
//                        System.out.println("HelloWorld2 String = " + str);
//                end
//            """
//
//        then:
//            droolsUtility.addRulesFromString("/rule1.drl", rule1)
//            droolsUtility.addRulesFromString("/rule2.drl", rule2)
//            droolsUtility.insertFact("Hello World")
//            droolsUtility.runRulesOnce()
//
//            droolsUtility.insertFact("Bye World")
//            droolsUtility.runRulesOnce()
//
//            droolsUtility.resetRules()
//            droolsUtility.addRulesFromString("/rule2.drl", rule2)
//            droolsUtility.insertFact("Hello World reload")
//            droolsUtility.insertFact("Reset")
//            droolsUtility.runRulesOnce()
//    }
//
//
//    def "Testing Sum"() {
//        when:
//        def droolsUtility = new DroolsUtility()
//
//        droolsUtility.addRuleFromClassPath("SumDecimal.drl")
//        def decimalList = new ArrayList<BigDecimal>()
//        decimalList.add(new BigDecimal("12"))
//        decimalList.add(new BigDecimal("12"))
//        decimalList.add(new BigDecimal("12"))
//
//        droolsUtility.insertFact(decimalList)
//        droolsUtility.runRulesOnce()
//
//
//        then:
//
//            println("sucess")
//    }
//
//
//}

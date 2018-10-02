//package org.jvmscript.drools;
//
//import java.io.IOException;
//import java.io.ObjectInput;
//import java.io.ObjectOutput;
//import java.io.Serializable;
//import java.math.BigDecimal;
//
//import org.kie.api.runtime.rule.AccumulateFunction;
//
//public class BigDecimalAccumulator implements AccumulateFunction {
//
//     @Override
//    public Serializable createContext() {
//        return new BigDecimalSum();
//    }
//
//    @Override
//    public void init(Serializable context) throws Exception {
//        BigDecimalSum accumulator = (BigDecimalSum) context;
//        accumulator.init();
//    }
//
//    @Override
//    public void accumulate(Serializable context, Object value) {
//        BigDecimalSum accumulator = (BigDecimalSum) context;
//        accumulator.add((BigDecimal) value);
//    }
//
//    @Override
//    public void reverse(Serializable context, Object value) throws Exception {
//        BigDecimalSum accumulator = (BigDecimalSum) context;
//        accumulator.subtract((BigDecimal) value);
//    }
//
//    @Override
//    public boolean supportsReverse() {
//        return true;
//    }
//
//     @Override
//    public Object getResult(Serializable context) throws Exception {
//        BigDecimalSum accumulator = (BigDecimalSum) context;
//        return accumulator.sum;
//    }
//
//    @Override
//    public Class<BigDecimal> getResultType() {
//        return BigDecimal.class;
//    }
//
//    @Override
//    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//    }
//
//    @Override
//    public void writeExternal(ObjectOutput out) throws IOException {
//    }
//
//    private static class BigDecimalSum implements Serializable {
//        /** Generated serialVersionUID */
//        private static final long serialVersionUID = -3852330030144129793L;
//        BigDecimal sum = BigDecimal.ZERO;
//        void init() {
//            this.sum = BigDecimal.ZERO;
//        }
//        void add(BigDecimal augend) {
//            this.sum = sum.add(augend);
//        }
//        void subtract(BigDecimal subtrahend) {
//            this.sum = sum.subtract(subtrahend);
//        }
//    }
//}
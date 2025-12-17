package org.jvmscript.dataframe;

import org.dflib.DataFrame;

import java.util.ArrayList;
import java.util.List;

public class LoadResult {
    public DataFrame validData;
    public List<BadRow> parseErrors;
    public List<BadRow> validationErrors;

    public LoadResult() {
        this.parseErrors = new ArrayList<>();
        this.validationErrors = new ArrayList<>();
    }

    public int getTotalBadRows() {
        return parseErrors.size() + validationErrors.size();
    }

    public List<BadRow> getAllBadRows() {
        List<BadRow> all = new ArrayList<>();
        all.addAll(parseErrors);
        all.addAll(validationErrors);
        return all;
    }
}

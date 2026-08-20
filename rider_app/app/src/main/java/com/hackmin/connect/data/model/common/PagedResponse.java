package com.hackmin.connect.data.model.common;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Matches the common list envelope from the README:
 * { "count": ..., "next": ..., "previous": ..., "results": [...] }
 * Some simple list endpoints only return { "results": [...] }, which still
 * deserializes fine here since count/next/previous are nullable/primitive-boxed.
 */
public class PagedResponse<T> {

    @SerializedName("count")
    private Integer count;

    @SerializedName("next")
    private String next;

    @SerializedName("previous")
    private String previous;

    @SerializedName("results")
    private List<T> results;

    public Integer getCount() { return count; }
    public String getNext() { return next; }
    public String getPrevious() { return previous; }
    public List<T> getResults() { return results; }
}

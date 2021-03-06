package com.microsoft.azure.documentdb.internal.routing;

import java.util.*;

import com.microsoft.azure.documentdb.PartitionKeyRange;

public final class RoutingMapProviderHelper {
    private static final Range.MaxComparator<String> MAX_COMPARATOR = new Range.MaxComparator<String>();

    private static String max(String left, String right) {
        return left.compareTo(right) < 0 ? right : left;
    }

    private static <T extends Comparable<T>> boolean IsSortedAndNonOverlapping(List<Range<T>> list) {
        for (int i = 1; i < list.size(); i++) {
            Range<T> previousRange = list.get(i - 1);
            Range<T> currentRange = list.get(i);

            int compareResult = previousRange.getMax().compareTo(currentRange.getMin());
            if (compareResult > 0) {
                return false;
            } else if (compareResult == 0 && previousRange.isMaxInclusive() && currentRange.isMinInclusive()) {
                return false;
            }
        }

        return true;
    }

    public static Collection<PartitionKeyRange> getOverlappingRanges(RoutingMapProvider routingMapProvider,
            String resourceLink, List<Range<String>> sortedRanges) {
        if (!IsSortedAndNonOverlapping(sortedRanges)) {
            throw new IllegalArgumentException("sortedRanges");
        }

        List<PartitionKeyRange> targetRanges = new ArrayList<PartitionKeyRange>();
        int currentProvidedRange = 0;
        while (currentProvidedRange < sortedRanges.size()) {
            if (sortedRanges.get(currentProvidedRange).isEmpty()) {
                currentProvidedRange++;
                continue;
            }

            Range<String> queryRange;
            if (!targetRanges.isEmpty()) {
                String left = max(targetRanges.get(targetRanges.size() - 1).getMaxExclusive(),
                        sortedRanges.get(currentProvidedRange).getMin());

                boolean leftInclusive = left.compareTo(sortedRanges.get(currentProvidedRange).getMin()) == 0
                        ? sortedRanges.get(currentProvidedRange).isMinInclusive() : false;

                queryRange = new Range<String>(left, sortedRanges.get(currentProvidedRange).getMax(), leftInclusive,
                        sortedRanges.get(currentProvidedRange).isMaxInclusive());
            } else {
                queryRange = sortedRanges.get(currentProvidedRange);
            }

            targetRanges.addAll(routingMapProvider.getOverlappingRanges(resourceLink, queryRange));

            Range<String> lastKnownTargetRange = targetRanges.get(targetRanges.size() - 1).toRange();
            while (currentProvidedRange < sortedRanges.size()
                    && MAX_COMPARATOR.compare(sortedRanges.get(currentProvidedRange), lastKnownTargetRange) <= 0) {
                currentProvidedRange++;
            }
        }

        return targetRanges;
    }
}

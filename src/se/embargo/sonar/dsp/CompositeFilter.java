package se.embargo.sonar.dsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CompositeFilter implements ISignalFilter {
	private List<ISignalFilter> _filters = new ArrayList<ISignalFilter>();

	public CompositeFilter(ISignalFilter... filters) {
		_filters.addAll(Arrays.asList(filters));
	}
	
	public CompositeFilter() {}
	
	public void add(ISignalFilter filter) {
		_filters.add(filter);
	}

	@Override
	public void accept(ISignalFilter.Item item) {
		for (ISignalFilter filter : _filters) {
			filter.accept(item);
		}
	}
}

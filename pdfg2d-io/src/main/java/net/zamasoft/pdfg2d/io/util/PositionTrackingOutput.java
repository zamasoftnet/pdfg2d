package net.zamasoft.pdfg2d.io.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.io.FragmentedOutput;

/**
 * A wrapper that adds position tracking support to any FragmentedOutput.
 * <p>
 * Use this class to wrap implementations that don't support position info
 * natively. It maintains its own linked list of fragments to calculate
 * positions when {@link #getPositionInfo()} is called.
 * </p>
 * <p>
 * After wrapping, {@link #supportsPositionInfo()} will return true.
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class PositionTrackingOutput extends FragmentedOutputWrapper {
	/** Debug flag for logging fragment statistics. */
	private static final boolean DEBUG = false;

	/** List of all fragment info indexed by ID. */
	private final List<FragmentInfo> fragments = new ArrayList<>();

	/** Linked list pointers for fragment ordering. */
	private FragmentInfo first = null, last = null;

	/**
	 * Internal record of a single fragment's position within the output order.
	 * <p>
	 * Instances are linked together into a doubly-linked list that reflects the
	 * logical output order, which may differ from creation order when
	 * {@link #insertFragmentBefore} is used.
	 * </p>
	 */
	private static class FragmentInfo {
		/** Immutable fragment ID assigned at creation time. */
		public final int id;

		/** Previous fragment in logical output order; {@code null} if this is the first. */
		public FragmentInfo prev = null;

		/** Next fragment in logical output order; {@code null} if this is the last. */
		public FragmentInfo next = null;

		/** Accumulated byte length of all data written to this fragment. */
		public long fragmentLength = 0;

		/**
		 * Creates a new fragment info.
		 * 
		 * @param id fragment ID.
		 */
		public FragmentInfo(final int id) {
			this.id = id;
		}
	}

	/**
	 * Creates a new position tracking wrapper.
	 * <p>
	 * The wrapped builder should not already support position info;
	 * this is verified with an assertion.
	 * </p>
	 * 
	 * @param builder the FragmentedOutput to wrap.
	 */
	public PositionTrackingOutput(final FragmentedOutput builder) {
		super(builder);
		assert !builder.supportsPositionInfo();
	}

	/**
	 * Returns the next fragment ID.
	 * 
	 * @return next available ID.
	 */
	protected int nextId() {
		return this.fragments.size();
	}

	/**
	 * Retrieves fragment info by ID.
	 * 
	 * @param id fragment ID.
	 * @return fragment info.
	 */
	protected FragmentInfo getFragment(final int id) {
		return this.fragments.get(id);
	}

	/**
	 * Stores fragment info.
	 * 
	 * @param id       fragment ID.
	 * @param fragment the fragment info to store.
	 */
	protected void putFragment(final int id, final FragmentInfo fragment) {
		assert (id == this.fragments.size());
		this.fragments.add(fragment);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Calculates positions by traversing the fragment linked list.
	 * </p>
	 */
	@Override
	public PositionInfo getPositionInfo() {
		final long[] idToPosition = new long[this.fragments.size()];
		long position = 0;
		var fragment = this.first;
		while (fragment != null) {
			idToPosition[fragment.id] = position;
			position += fragment.fragmentLength;
			fragment = fragment.next;
		}
		return id -> idToPosition[id];
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @return always true after wrapping.
	 */
	@Override
	public boolean supportsPositionInfo() {
		return true;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Creates a new fragment and appends it to the tracking list.
	 * </p>
	 */
	@Override
	public void addFragment() throws IOException {
		final int id = this.nextId();
		final var fragment = new FragmentInfo(id);
		if (this.first == null) {
			this.first = fragment;
		} else {
			this.last.next = fragment;
			fragment.prev = this.last;
		}
		this.putFragment(id, fragment);
		this.last = fragment;
		super.addFragment();
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Creates a new fragment and inserts it before the anchor in the tracking list.
	 * </p>
	 */
	@Override
	public void insertFragmentBefore(final int anchorId) throws IOException {
		final int id = this.nextId();
		final var anchor = this.getFragment(anchorId);
		final var fragment = new FragmentInfo(id);
		this.putFragment(id, fragment);
		// Insert into tracking linked list
		fragment.prev = anchor.prev;
		fragment.next = anchor;
		if (anchor.prev != null) {
			anchor.prev.next = fragment;
		}
		anchor.prev = fragment;
		if (this.first == anchor) {
			this.first = fragment;
		}
		super.insertFragmentBefore(anchorId);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Writes data to the wrapped output and updates the fragment length.
	 * </p>
	 */
	@Override
	public void write(final int id, final byte[] b, final int off, final int len) throws IOException {
		final var fragment = this.getFragment(id);
		fragment.fragmentLength += len;
		super.write(id, b, off, len);
	}

	/** {@inheritDoc} */
	@Override
	public void finishFragment(final int id) throws IOException {
		super.finishFragment(id);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Clears tracking data and closes the wrapped output.
	 * </p>
	 */
	@Override
	public void close() throws IOException {
		try {
			if (DEBUG) {
				final int total = this.fragments.size();
				System.out.println(total + " fragments were generated.");
			}
			// Clear tracking data
			this.first = null;
			this.last = null;
			this.fragments.clear();
		} finally {
			super.close();
		}
	}
}
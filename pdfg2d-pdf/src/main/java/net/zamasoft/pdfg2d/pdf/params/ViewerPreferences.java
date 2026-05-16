package net.zamasoft.pdfg2d.pdf.params;

/**
 * Viewer preferences for the PDF document.
 * 
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class ViewerPreferences {
	private boolean hideToolbar = false;

	private boolean hideMenubar = false;

	private boolean hideWindowUI = false;

	private boolean fitWindow = false;

	private boolean centerWindow = false;

	// PDF 1.4
	private boolean displayDocTitle = false;

	private NonFullScreenPageMode nonFullScreenPageMode = NonFullScreenPageMode.NONE;

	public enum NonFullScreenPageMode {
		/**
		 * Neither document outline nor thumbnail images visible.
		 */
		NONE,
		/**
		 * Document outline visible.
		 */
		OUTLINES,
		/**
		 * Thumbnail images visible.
		 */
		THUMBS,
		/**
		 * Optional content group panel visible.
		 */
		OC;

	}

	// PDF 1.3
	private Direction direction = Direction.L2R;

	public enum Direction {
		/**
		 * Left to Right (e.g. English, Japanese horizontal).
		 */
		L2R,
		/**
		 * Right to Left (e.g. Arabic, Japanese vertical).
		 */
		R2L;

	}

	// PDF 1.4
	private AreaBox viewArea = AreaBox.CROP;
	private AreaBox viewClip = AreaBox.CROP;
	private AreaBox printArea = AreaBox.CROP;
	private AreaBox printClip = AreaBox.CROP;

	/**
	 * Identifies a page boundary box used for display or printing purposes.
	 */
	public enum AreaBox {
		/** Media box — the full physical page size. */
		MEDIA,
		/** Crop box — the visible page area (default). */
		CROP,
		/** Bleed box — the area to which page content may bleed. */
		BLEED,
		/** Trim box — the intended final page dimensions after trimming. */
		TRIM,
		/** Art box — the area of meaningful page content. */
		ART
	}

	// PDF 1.6
	private PrintScaling printScaling = PrintScaling.APP_DEFAULT;

	public enum PrintScaling {
		/**
		 * No scaling.
		 */
		NONE,
		/**
		 * Use viewer's default scaling.
		 */
		APP_DEFAULT;
	}

	// PDF 1.7
	private Duplex duplex = Duplex.NONE;

	public enum Duplex {
		/**
		 * Viewer's default.
		 */
		NONE,
		/**
		 * Simplex printing.
		 */
		SIMPLEX,
		/**
		 * Duplex printing, flip on short edge.
		 */
		FLIP_SHORT_EDGE,
		/**
		 * Duplex printing, flip on long edge.
		 */
		FLIP_LONG_EDGE;
	}

	// PDF 1.7
	private boolean pickTrayByPDFSize;

	// PDF 1.7
	private int[] printPageRange = null;

	// PDF 1.7
	private int numCopies = 0;

	/**
	 * Returns the page reading direction.
	 * 
	 * @return the direction
	 */
	public Direction getDirection() {
		return this.direction;
	}

	/**
	 * Sets the page reading direction.
	 * 
	 * @param direction the direction to set
	 */
	public void setDirection(final Direction direction) {
		this.direction = direction;
	}

	/**
	 * Returns whether the viewer application's toolbars are hidden.
	 *
	 * @return {@code true} if the toolbar is hidden
	 */
	public boolean isHideToolbar() {
		return this.hideToolbar;
	}

	/**
	 * Sets whether to hide the viewer application's toolbars.
	 * Note: Behavior in Adobe Reader may vary.
	 * 
	 * @param hideToolbar true to hide toolbar
	 */
	public void setHideToolbar(final boolean hideToolbar) {
		this.hideToolbar = hideToolbar;
	}

	/**
	 * Returns whether the viewer application's menu bar is hidden.
	 *
	 * @return {@code true} if the menu bar is hidden
	 */
	public boolean isHideMenubar() {
		return this.hideMenubar;
	}

	/**
	 * Sets whether to hide the viewer application's menu bar.
	 * 
	 * @param hideMenubar true to hide menu bar
	 */
	public void setHideMenubar(final boolean hideMenubar) {
		this.hideMenubar = hideMenubar;
	}

	/**
	 * Returns whether user interface elements in the document's window (such as
	 * scroll bars and navigation controls) are hidden.
	 *
	 * @return {@code true} if the window UI is hidden
	 */
	public boolean isHideWindowUI() {
		return this.hideWindowUI;
	}

	/**
	 * Sets whether to hide user interface elements in the document's window (such
	 * as scroll bars and navigation controls), leaving only the document's contents
	 * displayed.
	 * 
	 * @param hideWindowUI true to hide window UI
	 */
	public void setHideWindowUI(final boolean hideWindowUI) {
		this.hideWindowUI = hideWindowUI;
	}

	/**
	 * Returns whether the document's window is resized to fit the first displayed
	 * page.
	 *
	 * @return {@code true} if the window is fitted to the page
	 */
	public boolean isFitWindow() {
		return this.fitWindow;
	}

	/**
	 * Sets whether to resize the document's window to fit the size of the first
	 * displayed page.
	 * 
	 * @param fitWindow true to fit window
	 */
	public void setFitWindow(final boolean fitWindow) {
		this.fitWindow = fitWindow;
	}

	/**
	 * Returns whether the document's window is centered on the screen.
	 *
	 * @return {@code true} if the window is centered
	 */
	public boolean isCenterWindow() {
		return this.centerWindow;
	}

	/**
	 * Sets whether to position the document's window in the center of the screen.
	 * 
	 * @param centerWindow true to center window
	 */
	public void setCenterWindow(final boolean centerWindow) {
		this.centerWindow = centerWindow;
	}

	/**
	 * Returns whether the document's title is displayed in the window title bar
	 * instead of the filename.
	 *
	 * @return {@code true} if the document title is displayed
	 */
	public boolean isDisplayDocTitle() {
		return this.displayDocTitle;
	}

	/**
	 * Sets whether to display the document's title in the window title bar.
	 * 
	 * @param displayDocTitle true to display document title
	 */
	public void setDisplayDocTitle(final boolean displayDocTitle) {
		this.displayDocTitle = displayDocTitle;
	}

	/**
	 * Returns the page mode to use when exiting full-screen mode.
	 *
	 * @return the non-full-screen page mode
	 */
	public NonFullScreenPageMode getNonFullScreenPageMode() {
		return this.nonFullScreenPageMode;
	}

	/**
	 * Sets the page mode when exiting full-screen mode.
	 * Note: Behavior in Adobe Reader may vary.
	 * 
	 * @param nonFullScreenPageMode the mode
	 */
	public void setNonFullScreenPageMode(final NonFullScreenPageMode nonFullScreenPageMode) {
		this.nonFullScreenPageMode = nonFullScreenPageMode;
	}

	/**
	 * Returns the page boundary box used to determine the region of the page to
	 * display on screen.
	 *
	 * @return the view area box
	 */
	public AreaBox getViewArea() {
		return this.viewArea;
	}

	/**
	 * Sets the page boundary box used to determine the region of the page to
	 * display on screen.
	 *
	 * @param viewArea the view area box
	 */
	public void setViewArea(final AreaBox viewArea) {
		this.viewArea = viewArea;
	}

	/**
	 * Returns the page boundary box used to clip the display of page content.
	 *
	 * @return the view clip box
	 */
	public AreaBox getViewClip() {
		return this.viewClip;
	}

	/**
	 * Sets the page boundary box used to clip the display of page content.
	 *
	 * @param viewClip the view clip box
	 */
	public void setViewClip(final AreaBox viewClip) {
		this.viewClip = viewClip;
	}

	/**
	 * Returns the page boundary box used to determine the region of the page to
	 * render when printing.
	 *
	 * @return the print area box
	 */
	public AreaBox getPrintArea() {
		return this.printArea;
	}

	/**
	 * Sets the page boundary box used to determine the region of the page to
	 * render when printing.
	 *
	 * @param printArea the print area box
	 */
	public void setPrintArea(final AreaBox printArea) {
		this.printArea = printArea;
	}

	/**
	 * Returns the page boundary box used to clip the printing of page content.
	 *
	 * @return the print clip box
	 */
	public AreaBox getPrintClip() {
		return this.printClip;
	}

	/**
	 * Sets the page boundary box used to clip the printing of page content.
	 *
	 * @param printClip the print clip box
	 */
	public void setPrintClip(final AreaBox printClip) {
		this.printClip = printClip;
	}

	/**
	 * Returns the page scaling option used when printing.
	 *
	 * @return the print scaling option
	 */
	public PrintScaling getPrintScaling() {
		return this.printScaling;
	}

	/**
	 * Sets the page scaling option for printing.
	 * 
	 * @param printScaling the scaling option
	 */
	public void setPrintScaling(final PrintScaling printScaling) {
		this.printScaling = printScaling;
	}

	/**
	 * Returns the duplex printing mode.
	 *
	 * @return the duplex mode
	 */
	public Duplex getDuplex() {
		return this.duplex;
	}

	/**
	 * Sets the handling of paper handling for duplex printing.
	 * 
	 * @param duplex the duplex option
	 */
	public void setDuplex(final Duplex duplex) {
		this.duplex = duplex;
	}

	/**
	 * Returns whether the PDF page size should be used to select the input paper
	 * tray when printing.
	 *
	 * @return {@code true} if the paper tray is selected based on the PDF page size
	 */
	public boolean getPickTrayByPDFSize() {
		return this.pickTrayByPDFSize;
	}

	/**
	 * Sets whether the PDF page size is used to select the input paper tray.
	 * 
	 * @param pickTrayByPDFSize true to pick tray by PDF size
	 */
	public void setPickTrayByPDFSize(final boolean pickTrayByPDFSize) {
		this.pickTrayByPDFSize = pickTrayByPDFSize;
	}

	/**
	 * Returns the page ranges used to initialize the print dialog box.
	 * The array contains pairs of (start, end) page numbers, or {@code null} if
	 * no range restriction is set.
	 *
	 * @return array of page ranges, or {@code null}
	 */
	public int[] getPrintPageRange() {
		return this.printPageRange;
	}

	/**
	 * Sets the page numbers to initialize the print dialog box when the file is
	 * printed.
	 * The array must contain an even number of integers, treating them as pairs of
	 * (start, end).
	 * 
	 * @param printPageRange array of page ranges
	 */
	public void setPrintPageRange(final int[] printPageRange) {
		this.printPageRange = printPageRange;
	}

	/**
	 * Returns the number of copies to print.
	 * Returns {@code 0} to indicate the viewer's default.
	 *
	 * @return number of copies, or {@code 0} for viewer default
	 */
	public int getNumCopies() {
		return numCopies;
	}

	/**
	 * Sets the number of copies to print.
	 * 0 for viewer default, otherwise 2-5 are valid.
	 * 
	 * @param numCopies number of copies
	 * @throws IllegalArgumentException if numCopies is invalid
	 */
	public void setNumCopies(final int numCopies) {
		if (!(numCopies == 0 || (numCopies >= 2 && numCopies <= 5))) {
			throw new IllegalArgumentException();
		}
		this.numCopies = numCopies;
	}
}

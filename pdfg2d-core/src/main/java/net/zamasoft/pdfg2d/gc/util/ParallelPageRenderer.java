package net.zamasoft.pdfg2d.gc.util;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * Renders page content on multiple threads while keeping the output
 * strictly sequential: each page painter runs against a thread-confined
 * {@link RecorderGC}, and the recorded command list is replayed into the
 * real output in page order on the calling thread.
 * <p>
 * This parallelizes the CPU-bound part of page generation (geometry, text
 * shaping, painter logic) without requiring the output writer to be
 * thread-safe. Memory stays bounded: at most {@code parallelism * 2}
 * recorded pages are in flight.
 * </p>
 * <p>
 * Painters run concurrently and must not share mutable state; when text is
 * drawn, the supplied {@link FontManager} is shared between workers and must
 * tolerate concurrent metric lookups.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.2
 */
public final class ParallelPageRenderer {

	/** Paints the content of one page. */
	@FunctionalInterface
	public interface PagePainter {
		/**
		 * Draws the page content.
		 *
		 * @param gc the (recording) graphics context
		 * @throws GraphicsException if drawing fails
		 */
		void paint(GC gc) throws GraphicsException;
	}

	/** Supplies and finishes the real output pages, in order. */
	public interface PageTarget {
		/**
		 * Opens the next physical page.
		 *
		 * @return the graphics context of the page
		 * @throws GraphicsException if an error occurs
		 */
		GC nextPage() throws GraphicsException;

		/**
		 * Finishes the page returned by {@link #nextPage()}.
		 *
		 * @param gc the page context
		 * @throws GraphicsException if an error occurs
		 */
		void closePage(GC gc) throws GraphicsException;
	}

	private ParallelPageRenderer() {
		// static use only
	}

	/**
	 * Renders all pages produced by {@code painters} into {@code target}.
	 * Painting runs on {@code parallelism} threads; replay happens on the
	 * calling thread in submission order.
	 *
	 * @param painters    the page painters, one per page, in page order
	 * @param fontManager the font manager for text drawing, or {@code null}
	 *                    when no text is painted
	 * @param parallelism the number of worker threads
	 * @param target      the destination for the finished pages
	 * @throws GraphicsException if a painter or the target fails
	 */
	public static void render(final Iterator<? extends PagePainter> painters, final FontManager fontManager,
			final int parallelism, final PageTarget target) throws GraphicsException {
		if (parallelism < 1) {
			throw new IllegalArgumentException("parallelism must be >= 1");
		}
		final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
		try {
			// Bounded look-ahead window: limits buffered recorded pages
			final int window = parallelism * 2;
			final var pending = new ArrayDeque<Future<RecorderGC.Page>>(window);
			while (painters.hasNext() || !pending.isEmpty()) {
				while (painters.hasNext() && pending.size() < window) {
					final var painter = painters.next();
					pending.add(executor.submit(() -> {
						final var recorder = new RecorderGC(fontManager);
						painter.paint(recorder);
						return recorder.getPage();
					}));
				}
				final RecorderGC.Page page;
				try {
					page = pending.remove().get();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new GraphicsException(e);
				} catch (ExecutionException e) {
					final var cause = e.getCause();
					if (cause instanceof GraphicsException ge) {
						throw ge;
					}
					throw new GraphicsException(cause);
				}
				final var gc = target.nextPage();
				page.drawTo(gc);
				target.closePage(gc);
			}
		} finally {
			executor.shutdownNow();
		}
	}
}

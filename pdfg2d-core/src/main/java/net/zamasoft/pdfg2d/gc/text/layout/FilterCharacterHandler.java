package net.zamasoft.pdfg2d.gc.text.layout;

import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.CharacterHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * A pass-through {@link CharacterHandler} that forwards all events to a
 * delegate handler.
 * <p>
 * Subclasses override only the methods they need to intercept; all other events
 * are forwarded to the {@link #characterHandler} without modification.
 * This is the standard <em>Decorator</em> pattern applied to the character
 * pipeline.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @since 1.0
 */
public class FilterCharacterHandler implements CharacterHandler {
	/** The downstream handler to which all events are forwarded. */
	protected CharacterHandler characterHandler;

	/**
	 * Constructs a filter that forwards to {@code characterHandler}.
	 *
	 * @param characterHandler the downstream handler; must not be {@code null}
	 */
	public FilterCharacterHandler(final CharacterHandler characterHandler) {
		this.setCharacterHandler(characterHandler);
	}

	/**
	 * Constructs a filter with no initial downstream handler.
	 * {@link #setCharacterHandler(CharacterHandler)} must be called before
	 * forwarding any events.
	 */
	public FilterCharacterHandler() {
		// default
	}

	/**
	 * Returns the current downstream handler.
	 *
	 * @return the downstream {@link CharacterHandler}
	 */
	public CharacterHandler getCharacterHandler() {
		return this.characterHandler;
	}

	/**
	 * Replaces the downstream handler.
	 *
	 * @param characterHandler the new downstream handler; must not be {@code null}
	 */
	public void setCharacterHandler(final CharacterHandler characterHandler) {
		this.characterHandler = characterHandler;
	}

	@Override
	public void characters(final int charOffset, final char[] ch, final int off, final int len) {
		this.characterHandler.characters(charOffset, ch, off, len);
	}

	@Override
	public void control(final TextControl control) {
		this.characterHandler.control(control);
	}

	@Override
	public void fontStyle(final FontStyle fontStyle) {
		this.characterHandler.fontStyle(fontStyle);
	}

	@Override
	public void flush() {
		this.characterHandler.flush();
	}

	@Override
	public void close() {
		this.characterHandler.flush();
	}
}

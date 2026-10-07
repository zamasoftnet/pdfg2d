/*
 * Copyright Zamasoft. Licensed under the Apache License, Version 2.0.
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;

/**
 * Keeps a second language of Javadoc in a translation file and generates translated sources for javadoc.
 *
 * <p>
 * The source comments are the master (English). The translation file {@code javadoc/ja.json} maps a syntactic
 * signature of each published element to {@code {"hash", "en", "ja"}}: the hash and text of the English comment
 * the translation was made from, and the translation. A translation whose hash no longer matches the source is
 * stale and is not used. See {@code copperpdf4/docs/design/javadoc-i18n-design.md}.
 * </p>
 *
 * <pre>
 * java JavadocI18n.java extract --src DIR... --scope PKG,... -o FILE   # take the current comments as translations
 * java JavadocI18n.java pair    --src DIR... --ja FILE                 # attach the (new) source text and hash
 * java JavadocI18n.java update  --src DIR... --scope PKG,... --ja FILE # add missing keys, mark stale ones
 * java JavadocI18n.java check   --src DIR... --scope PKG,... --ja FILE # fail on missing or stale translations
 * java JavadocI18n.java inject  --src DIR... --ja FILE -o DIR          # write sources with translated comments
 * </pre>
 *
 * <p>
 * A scope entry is a package name, or a package name followed by {@code .**} for the package and its subpackages.
 * Only elements that javadoc publishes by default (public and protected, in public or protected types) are in scope.
 * The tool parses only; it does not resolve types, so it runs on any source tree without a classpath.
 * </p>
 */
public final class JavadocI18n {
	private JavadocI18n() {
	}

	/** One documented declaration: where its comment is and what it says. */
	record Doc(String key, Path file, int start, int end, String indent, String text, boolean visible) {
	}

	public static void main(final String[] args) throws Exception {
		if (args.length == 0) {
			usage();
		}
		final String mode = args[0];
		final List<Path> srcs = new ArrayList<>();
		final List<String> scope = new ArrayList<>();
		Path ja = null, out = null;
		for (int i = 1; i < args.length; ++i) {
			switch (args[i]) {
			case "--src" -> srcs.add(Path.of(args[++i]));
			case "--scope" -> scope.addAll(List.of(args[++i].split(",")));
			case "--ja" -> ja = Path.of(args[++i]);
			case "-o" -> out = Path.of(args[++i]);
			default -> {
				if (!srcs.isEmpty() && !args[i].startsWith("-")) {
					srcs.add(Path.of(args[i]));
				} else {
					usage();
				}
			}
			}
		}
		if (srcs.isEmpty()) {
			usage();
		}
		final List<Doc> docs = scan(srcs);
		final int code = switch (mode) {
		case "extract" -> extract(docs, scope, require(out, "-o"));
		case "pair" -> pair(docs, require(ja, "--ja"));
		case "update" -> update(docs, scope, require(ja, "--ja"));
		case "check" -> check(docs, scope, require(ja, "--ja"));
		case "inject" -> inject(docs, srcs, require(ja, "--ja"), require(out, "-o"));
		default -> {
			usage();
			yield 2;
		}
		};
		System.exit(code);
	}

	private static Path require(final Path p, final String option) {
		if (p == null) {
			System.err.println("missing " + option);
			System.exit(2);
		}
		return p;
	}

	private static void usage() {
		System.err.println("usage: JavadocI18n extract|pair|update|check|inject --src DIR... [--scope PKG,...] "
				+ "[--ja FILE] [-o FILE|DIR]");
		System.exit(2);
	}

	// ------------------------------------------------------------------ modes

	/** Hiragana, katakana or CJK ideographs: the comment is (at least partly) Japanese. */
	private static final java.util.regex.Pattern JAPANESE = java.util.regex.Pattern
			.compile("[\\u3040-\\u30ff\\u4e00-\\u9fff]");

	private static int extract(final List<Doc> docs, final List<String> scope, final Path out) throws IOException {
		final Map<String, Map<String, Object>> map = new TreeMap<>();
		for (final Doc d : docs) {
			// Comments already in English get their Japanese later through update
			if (inScope(d, scope) && JAPANESE.matcher(d.text).find()) {
				final Map<String, Object> e = new LinkedHashMap<>();
				e.put("ja", d.text);
				map.put(d.key, e);
			}
		}
		writeJson(out, map);
		System.out.println("extracted " + map.size() + " comments to " + out);
		return 0;
	}

	private static int pair(final List<Doc> docs, final Path jaFile) throws IOException {
		final Map<String, Map<String, Object>> map = readJson(jaFile);
		final Map<String, Doc> byKey = index(docs);
		int paired = 0, missing = 0;
		for (final Map.Entry<String, Map<String, Object>> e : map.entrySet()) {
			final Doc d = byKey.get(e.getKey());
			if (d == null) {
				System.out.println("no source for " + e.getKey());
				++missing;
				continue;
			}
			e.getValue().put("hash", hash(d.text));
			e.getValue().put("en", d.text);
			e.getValue().remove("stale");
			++paired;
		}
		writeJson(jaFile, sorted(map));
		System.out.println("paired " + paired + ", without source " + missing);
		return 0;
	}

	private static int update(final List<Doc> docs, final List<String> scope, final Path jaFile) throws IOException {
		final Map<String, Map<String, Object>> map = Files.exists(jaFile) ? readJson(jaFile) : new TreeMap<>();
		int added = 0, staled = 0;
		for (final Doc d : docs) {
			if (!inScope(d, scope)) {
				continue;
			}
			final String h = hash(d.text);
			Map<String, Object> e = map.get(d.key);
			if (e == null) {
				e = new LinkedHashMap<>();
				e.put("hash", h);
				e.put("en", d.text);
				e.put("ja", "");
				map.put(d.key, e);
				++added;
			} else if (!h.equals(e.get("hash"))) {
				e.put("hash", h);
				e.put("en", d.text);
				e.put("stale", Boolean.TRUE);
				++staled;
			}
		}
		writeJson(jaFile, sorted(map));
		System.out.println("added " + added + ", marked stale " + staled);
		return 0;
	}

	private static int check(final List<Doc> docs, final List<String> scope, final Path jaFile) throws IOException {
		final Map<String, Map<String, Object>> map = Files.exists(jaFile) ? readJson(jaFile) : new TreeMap<>();
		int missing = 0, stale = 0, total = 0;
		final Set<String> seen = new java.util.HashSet<>();
		for (final Doc d : docs) {
			if (!inScope(d, scope)) {
				continue;
			}
			++total;
			seen.add(d.key);
			final Map<String, Object> e = map.get(d.key);
			if (e == null || isBlank(e.get("ja"))) {
				System.out.println("missing: " + d.key);
				++missing;
			} else if (Boolean.TRUE.equals(e.get("stale")) || !hash(d.text).equals(e.get("hash"))) {
				System.out.println("stale:   " + d.key);
				++stale;
			}
		}
		int orphan = 0;
		for (final String k : map.keySet()) {
			if (!seen.contains(k)) {
				System.out.println("orphan:  " + k);
				++orphan;
			}
		}
		System.out.println("in scope " + total + ", missing " + missing + ", stale " + stale + ", orphan " + orphan);
		return missing + stale + orphan == 0 ? 0 : 1;
	}

	private static int inject(final List<Doc> docs, final List<Path> srcs, final Path jaFile, final Path out)
			throws IOException {
		final Map<String, Map<String, Object>> map = readJson(jaFile);
		final Map<Path, List<Doc>> byFile = new LinkedHashMap<>();
		for (final Doc d : docs) {
			byFile.computeIfAbsent(d.file, f -> new ArrayList<>()).add(d);
		}
		int replaced = 0, kept = 0;
		for (int r = 0; r < srcs.size(); ++r) {
			final Path root = srcs.get(r);
			final Path dest = srcs.size() == 1 ? out : out.resolve(Integer.toString(r));
			try (Stream<Path> walk = Files.walk(root)) {
				for (final Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
					final Path target = dest.resolve(root.relativize(p).toString());
					Files.createDirectories(target.getParent());
					final List<Doc> list = byFile.get(p.toAbsolutePath().normalize());
					if (list == null) {
						Files.copy(p, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
						continue;
					}
					String src = Files.readString(p, StandardCharsets.UTF_8);
					// Replace from the end so that earlier offsets stay valid
					final List<Doc> ordered = new ArrayList<>(list);
					ordered.sort((a, b) -> Integer.compare(b.start, a.start));
					for (final Doc d : ordered) {
						final Map<String, Object> e = map.get(d.key);
						if (e == null || isBlank(e.get("ja")) || Boolean.TRUE.equals(e.get("stale"))
								|| !hash(d.text).equals(e.get("hash"))) {
							++kept;
							continue;
						}
						src = src.substring(0, d.start) + render((String) e.get("ja"), d.indent) + src.substring(d.end);
						++replaced;
					}
					Files.writeString(target, src, StandardCharsets.UTF_8);
				}
			}
		}
		System.out.println("translated " + replaced + " comments, kept " + kept + " in the source language");
		return 0;
	}

	// ------------------------------------------------------------------ scanning

	private static List<Doc> scan(final List<Path> srcs) throws IOException {
		final List<Path> files = new ArrayList<>();
		for (final Path root : srcs) {
			try (Stream<Path> walk = Files.walk(root)) {
				walk.filter(p -> p.toString().endsWith(".java")).map(p -> p.toAbsolutePath().normalize())
						.forEach(files::add);
			}
		}
		final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		final List<Doc> docs = new ArrayList<>();
		try (StandardJavaFileManager fm = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
			final Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromPaths(files);
			final JavacTask task = (JavacTask) javac.getTask(null, fm, d -> {
			}, List.of("-proc:none"), null, units);
			final Trees trees = Trees.instance(task);
			final SourcePositions pos = trees.getSourcePositions();
			for (final CompilationUnitTree cu : task.parse()) {
				final Path file = Path.of(cu.getSourceFile().toUri()).toAbsolutePath().normalize();
				final String text = cu.getSourceFile().getCharContent(true).toString();
				final String pkg = cu.getPackageName() == null ? "" : cu.getPackageName().toString();
				if (file.getFileName().toString().equals("package-info.java") && cu.getPackage() != null) {
					add(docs, file, text, (int) pos.getStartPosition(cu, cu.getPackage()), pkg, true);
				}
				for (final Tree t : cu.getTypeDecls()) {
					if (t instanceof ClassTree c) {
						scanClass(docs, file, text, pos, cu, c, pkg.isEmpty() ? "" : pkg + ".", true, false);
					}
				}
			}
		}
		return docs;
	}

	private static void scanClass(final List<Doc> docs, final Path file, final String text, final SourcePositions pos,
			final CompilationUnitTree cu, final ClassTree c, final String prefix, final boolean outerVisible,
			final boolean inInterface) {
		final Set<Modifier> mods = c.getModifiers().getFlags();
		final boolean visible = outerVisible && (mods.contains(Modifier.PUBLIC) || mods.contains(Modifier.PROTECTED)
				|| inInterface);
		final String key = prefix + c.getSimpleName();
		add(docs, file, text, (int) pos.getStartPosition(cu, c), key, visible);
		final boolean iface = c.getKind() == Tree.Kind.INTERFACE || c.getKind() == Tree.Kind.ANNOTATION_TYPE;
		final boolean isEnum = c.getKind() == Tree.Kind.ENUM;
		for (final Tree m : c.getMembers()) {
			if (m instanceof ClassTree nested) {
				scanClass(docs, file, text, pos, cu, nested, key + ".", visible, iface);
			} else if (m instanceof MethodTree mt) {
				final Set<Modifier> f = mt.getModifiers().getFlags();
				final boolean v = visible && (iface || f.contains(Modifier.PUBLIC) || f.contains(Modifier.PROTECTED));
				final String name = mt.getName().contentEquals("<init>") ? c.getSimpleName().toString()
						: mt.getName().toString();
				final StringBuilder sig = new StringBuilder(key).append('#').append(name).append('(');
				for (int i = 0; i < mt.getParameters().size(); ++i) {
					if (i > 0) {
						sig.append(',');
					}
					sig.append(typeName(mt.getParameters().get(i).getType().toString()));
				}
				add(docs, file, text, (int) pos.getStartPosition(cu, mt), sig.append(')').toString(), v);
			} else if (m instanceof VariableTree vt) {
				final Set<Modifier> f = vt.getModifiers().getFlags();
				final boolean constant = isEnum && isEnumConstant(text, (int) pos.getStartPosition(cu, vt), vt);
				final boolean v = visible
						&& (iface || constant || f.contains(Modifier.PUBLIC) || f.contains(Modifier.PROTECTED));
				add(docs, file, text, (int) pos.getStartPosition(cu, vt), key + "#" + vt.getName(), v);
			}
		}
	}

	/** Enum constants have no explicit modifiers or type in the source. */
	private static boolean isEnumConstant(final String text, final int start, final VariableTree vt) {
		return vt.getModifiers().getFlags().isEmpty() && start >= 0
				&& text.startsWith(vt.getName().toString(), start);
	}

	/** Drops generics and annotations so that keys do not change with them. */
	static String typeName(final String t) {
		String s = t.replaceAll("@[\\w.]+(\\([^)]*\\))?\\s*", "");
		int depth = 0;
		final StringBuilder b = new StringBuilder();
		for (final char ch : s.toCharArray()) {
			if (ch == '<') {
				++depth;
			} else if (ch == '>') {
				--depth;
			} else if (depth == 0 && !Character.isWhitespace(ch)) {
				b.append(ch);
			}
		}
		return b.toString();
	}

	/**
	 * Adds the doc comment that ends right before {@code declStart} (only whitespace in between). The declaration
	 * start includes its annotations, and a doc comment always precedes them.
	 */
	private static void add(final List<Doc> docs, final Path file, final String text, final int declStart,
			final String key, final boolean visible) {
		if (declStart <= 0) {
			return;
		}
		int i = declStart - 1;
		while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
			--i;
		}
		if (i < 1 || text.charAt(i) != '/' || text.charAt(i - 1) != '*') {
			return;
		}
		final int end = i + 1;
		final int start = text.lastIndexOf("/**", i - 1);
		if (start < 0 || text.indexOf("*/", start + 2) != i - 1) {
			return;
		}
		final int lineStart = text.lastIndexOf('\n', start) + 1;
		final String indent = text.substring(lineStart, start).replaceAll("\\S.*", "");
		docs.add(new Doc(key, file, start, end, indent, normalize(text.substring(start + 3, end - 2)), visible));
	}

	/** Strips the leading {@code *} decoration and surrounding blank lines of a comment body. */
	static String normalize(final String body) {
		final List<String> lines = new ArrayList<>();
		for (final String raw : body.split("\r?\n", -1)) {
			String l = raw.stripLeading();
			if (l.startsWith("*")) {
				l = l.substring(1);
				if (l.startsWith(" ")) {
					l = l.substring(1);
				}
			}
			lines.add(l.stripTrailing());
		}
		while (!lines.isEmpty() && lines.get(0).isEmpty()) {
			lines.remove(0);
		}
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
			lines.remove(lines.size() - 1);
		}
		return String.join("\n", lines);
	}

	/** Writes a comment body back as a doc comment at the given indentation. */
	static String render(final String body, final String indent) {
		final StringBuilder b = new StringBuilder("/**\n");
		for (final String l : body.split("\n", -1)) {
			b.append(indent).append(l.isEmpty() ? " *" : " * " + l).append('\n');
		}
		return b.append(indent).append(" */").toString();
	}

	private static boolean inScope(final Doc d, final List<String> scope) {
		if (!d.visible) {
			return false;
		}
		if (scope.isEmpty()) {
			return true;
		}
		final String pkg = packageOf(d.key);
		for (final String s : scope) {
			if (s.endsWith(".**") ? pkg.equals(s.substring(0, s.length() - 3))
					|| pkg.startsWith(s.substring(0, s.length() - 2)) : pkg.equals(s)) {
				return true;
			}
		}
		return false;
	}

	/** The package part of a key: the dotted prefix before the first segment starting with an upper-case letter. */
	static String packageOf(final String key) {
		final String head = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
		final String[] parts = head.split("\\.");
		final StringBuilder b = new StringBuilder();
		for (final String p : parts) {
			if (!p.isEmpty() && Character.isUpperCase(p.charAt(0))) {
				break;
			}
			if (b.length() > 0) {
				b.append('.');
			}
			b.append(p);
		}
		return b.toString();
	}

	private static Map<String, Doc> index(final List<Doc> docs) {
		final Map<String, Doc> m = new LinkedHashMap<>();
		for (final Doc d : docs) {
			m.putIfAbsent(d.key, d);
		}
		return m;
	}

	static String hash(final String text) {
		try {
			final byte[] h = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(h).substring(0, 16);
		} catch (final NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	private static boolean isBlank(final Object o) {
		return o == null || o.toString().isBlank();
	}

	private static Map<String, Map<String, Object>> sorted(final Map<String, Map<String, Object>> map) {
		final Map<String, Map<String, Object>> s = new TreeMap<>();
		for (final Map.Entry<String, Map<String, Object>> e : map.entrySet()) {
			final Map<String, Object> v = new LinkedHashMap<>();
			for (final String k : List.of("hash", "stale", "en", "ja")) {
				if (e.getValue().containsKey(k)) {
					v.put(k, e.getValue().get(k));
				}
			}
			s.put(e.getKey(), v);
		}
		return s;
	}

	// ------------------------------------------------------------------ minimal JSON (object of objects)

	static void writeJson(final Path file, final Map<String, Map<String, Object>> map) throws IOException {
		final StringBuilder b = new StringBuilder("{\n");
		int i = 0;
		for (final Map.Entry<String, Map<String, Object>> e : map.entrySet()) {
			b.append("  ").append(quote(e.getKey())).append(": {");
			int j = 0;
			for (final Map.Entry<String, Object> f : e.getValue().entrySet()) {
				b.append(j++ == 0 ? "\n" : ",\n").append("    ").append(quote(f.getKey())).append(": ");
				b.append(f.getValue() instanceof Boolean ? f.getValue().toString() : quote(f.getValue().toString()));
			}
			b.append("\n  }").append(++i < map.size() ? ",\n" : "\n");
		}
		b.append("}\n");
		if (file.getParent() != null) {
			Files.createDirectories(file.getParent());
		}
		Files.writeString(file, b, StandardCharsets.UTF_8);
	}

	private static String quote(final String s) {
		final StringBuilder b = new StringBuilder("\"");
		for (final char c : s.toCharArray()) {
			switch (c) {
			case '"' -> b.append("\\\"");
			case '\\' -> b.append("\\\\");
			case '\n' -> b.append("\\n");
			case '\r' -> b.append("\\r");
			case '\t' -> b.append("\\t");
			default -> {
				if (c < 0x20) {
					b.append(String.format("\\u%04x", (int) c));
				} else {
					b.append(c);
				}
			}
			}
		}
		return b.append('"').toString();
	}

	static Map<String, Map<String, Object>> readJson(final Path file) throws IOException {
		final String s = Files.readString(file, StandardCharsets.UTF_8);
		final int[] at = { 0 };
		final Object v = parse(s, at);
		@SuppressWarnings("unchecked")
		final Map<String, Map<String, Object>> map = (Map<String, Map<String, Object>>) v;
		return map;
	}

	private static Object parse(final String s, final int[] at) {
		skip(s, at);
		final char c = s.charAt(at[0]);
		if (c == '{') {
			++at[0];
			final Map<String, Object> m = new LinkedHashMap<>();
			skip(s, at);
			if (s.charAt(at[0]) == '}') {
				++at[0];
				return m;
			}
			while (true) {
				skip(s, at);
				final String k = (String) parse(s, at);
				skip(s, at);
				expect(s, at, ':');
				m.put(k, parse(s, at));
				skip(s, at);
				if (s.charAt(at[0]) == ',') {
					++at[0];
					continue;
				}
				expect(s, at, '}');
				return m;
			}
		}
		if (c == '"') {
			final StringBuilder b = new StringBuilder();
			++at[0];
			while (true) {
				final char ch = s.charAt(at[0]++);
				if (ch == '"') {
					return b.toString();
				}
				if (ch != '\\') {
					b.append(ch);
					continue;
				}
				final char esc = s.charAt(at[0]++);
				switch (esc) {
				case 'n' -> b.append('\n');
				case 'r' -> b.append('\r');
				case 't' -> b.append('\t');
				case 'b' -> b.append('\b');
				case 'f' -> b.append('\f');
				case 'u' -> {
					b.append((char) Integer.parseInt(s.substring(at[0], at[0] + 4), 16));
					at[0] += 4;
				}
				default -> b.append(esc);
				}
			}
		}
		if (s.startsWith("true", at[0])) {
			at[0] += 4;
			return Boolean.TRUE;
		}
		if (s.startsWith("false", at[0])) {
			at[0] += 5;
			return Boolean.FALSE;
		}
		throw new UncheckedIOException(new IOException("unexpected JSON at " + at[0]));
	}

	private static void skip(final String s, final int[] at) {
		while (at[0] < s.length() && Character.isWhitespace(s.charAt(at[0]))) {
			++at[0];
		}
	}

	private static void expect(final String s, final int[] at, final char c) {
		if (s.charAt(at[0]) != c) {
			throw new UncheckedIOException(new IOException("expected " + c + " at " + at[0]));
		}
		++at[0];
	}
}

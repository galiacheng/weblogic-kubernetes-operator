// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.mojo.shunit2;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities related to ANSI-formatting of strings sent to a terminal.
 */
class AnsiUtils {

  private static final Pattern ANSI_ESCAPE_CHARS = Pattern.compile("(\\x9B|\\x1B\\[)[0-?]*[ -\\/]*[@-~]");

  static String withoutAnsiEscapeChars(String input) {
    final Matcher matcher = ANSI_ESCAPE_CHARS.matcher(input);
    return matcher.replaceAll("");
  }

  public static AnsiFormatter text(String s) {
    return new AnsiFormatter(s);
  }

  static class AnsiFormatter {

    private final String string;
    private final List<Format> formats = new ArrayList<>();

    AnsiFormatter(String string) {
      this.string = string;
    }

    AnsiFormatter asBold() {
      formats.add(Format.BOLD);
      return this;
    }

    AnsiFormatter asRed() {
      formats.add(Format.RED_FOREGROUND);
      return this;
    }

    AnsiFormatter asBlue() {
      formats.add(Format.BLUE_FOREGROUND);
      return this;
    }

    public AnsiFormatter asGreen() {
      formats.add(Format.GREEN_FOREGROUND);
      return this;
    }

    String format() {
      return startCodes() + string + endCodes();
    }

    private String startCodes() {
      return sequence(formats.stream().map(Format::getFormat).toArray(String[]::new));
    }

    private String endCodes() {
      return sequence("0");
    }

    String sequence(String... formatCodes) {
      return "\u001B[" + String.join(";", formatCodes) + "m";
    }
  }

  static enum Format {
    BOLD(1), RED_FOREGROUND(31), BLUE_FOREGROUND(34), GREEN_FOREGROUND(32);

    private final String format;
    Format(int format) {
      this.format = Integer.toString(format);
    }

    public String getFormat() {
      return format;
    }
  }
}

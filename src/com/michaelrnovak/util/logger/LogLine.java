/*
 * Copyright (C) 2015 Gavriel Fleischer <flocsy@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package com.michaelrnovak.util.logger;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Gavriel Fleischer <flocsy@gmail.com> on 2015-07-06.
 */
public class LogLine {
    static final String DATE_FORMAT = "MM-dd HH:mm:ss.SSS";
    static final DateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT);

    protected final int pid;
    protected final char level;
    protected final String tag;
    protected final String text;

    public static class Brief extends LogLine {
        // V/Logger( 4973): log message
        public static Brief fromString(final String line) throws ParseException {
            char level = line.charAt(0);
            String tag = line.substring(2, line.indexOf('(')).trim();
            int pid = getInt(line, tag.length() + 4, ' ', ')');
            String text = line.substring(line.indexOf(':', tag.length() + 5) + 2);
            return new Brief(level, tag, pid, text);
        }
        public Brief(char level, String tag, int pid, String text) {
            super(level, tag, pid, text);
        }
    }

    public static class Time extends Brief {
        protected final Date date;
        //                    V/Logger( 4973): log message
        // 07-06 16:45:25.447 W/Logger( 5015): log message
        public static Time fromString(final String line) throws ParseException {
            Date date = parseDate(line.substring(0, 18));
            char level = line.charAt(0 + 19);
            String tag = line.substring(2 + 19, line.indexOf('(')).trim();
            int pid = getInt(line, tag.length() + 4 + 19, ' ', ')');
            String text = line.substring(line.indexOf(':', tag.length() + 5+19) + 2);
            return new Time(date, level, tag, pid, text);
        }
        public Time(Date date, char level, String tag, int pid, String text) {
            super(level, tag, pid, text);
            this.date = date;
        }
        public Date getDate() {
            return date;
        }
        @Override
        public String getHeader() {
            return date.toString() + ' ' + super.getHeader();
        }
    }

    public static class Long extends Time {
        protected final StringBuilder sb;

        // [ 07-06 13:21:53.668 19517:19581 W/Logger ]\nlog message
        public static Long fromString(final String line) throws ParseException {
            Date date = parseDate(line.substring(2, 20));
            int pid = getInt(line, 21, ' ', ':');
            char level = line.charAt(33);
            String tag = line.substring(35, line.length() - 2).trim();
            return new Long(date, pid, -1, level, tag, line);
        }
        public Long(Date date, int pid, int tid, char level, String tag) {
            super(date, level, tag, pid, null);
            sb = new StringBuilder();
        }
        public Long(Date date, int pid, int tid, char level, String tag, String text) {
            this(date, pid, tid, level, tag);
            sb.append(text);
        }
        public boolean add(final String line) {
            if (null != line) {
                sb.append('\n');
                sb.append(line);
                return true;
            } else {
                return false;
            }
        }
        @Override
        public String toString() {
            return sb.toString();
        }
        @Override
        public String getHeader() {
            final String str = toString();
            return str.substring(0, str.indexOf(']') + 1);
        }
    }

    public static LogLine fromString(final String line, final String format) throws ParseException {
        if ("brief".equals(format)) {
            return Brief.fromString(line);
        }
        else if ("time".equals(format)) {
            return Time.fromString(line);
        }
        else if ("long".equals(format)) {
            return Long.fromString(line);
        } else {
            return null;
        }
    }

    protected static int getInt(final String line, int offset, char before, char after) {
        while (line.charAt(offset) == before) { offset++; }
        final String intString = line.substring(offset, line.indexOf(after, offset));
        int integer = Integer.parseInt(intString);
        return integer;
    }

    private static Date parseDate(final String line) throws ParseException {
        Date date = SIMPLE_DATE_FORMAT.parse(line);
        Date now = new Date();
        int year = now.getYear();
        date.setYear(year);
        if (now.getTime() < date.getTime()) {
            date.setYear(year - 1);
        }
        return date;
    }

    protected LogLine(char level, String tag, int pid, String text) {
        this.level = level;
        this.tag = tag;
        this.pid = pid;
        this.text = text;
    }

    @Override
    public String toString() {
        return getHeader() + ' ' + text;
    }

    public int getPid() {
        return pid;
    }

    public char getLevel() {
        return level;
    }

    public String getTag() {
        return tag;
    }

    public String getHeader() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getLevel());
        sb.append('/');
        sb.append(getTag());
        sb.append('(');
        sb.append(getPid());
        sb.append(')');
        sb.append(':');
        return sb.toString();
    }
}

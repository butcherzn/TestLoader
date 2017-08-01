package com.joker.core.utils;

import android.content.Context;
import android.content.pm.PackageParser;
import android.os.Build;

import java.io.File;

/**
 * Created by joker on 2017/7/11.
 */

public final class PackageParserCompat {

    public static final PackageParser.Package parsePackage(final Context context, final File apk, final int flag) throws PackageParser.PackageParserException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return PackageParserV24.parsePackage(context, apk, flag);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return PackageParserV21.parsePackage(context, apk, flag);
        } else {
            return PackageParserLegacy.parsePackage(context, apk, flag);
        }
    }


    private static final class PackageParserV24 {

        static final PackageParser.Package parsePackage(Context context, File apk, int flags)
                throws PackageParser.PackageParserException {
            PackageParser parser = new PackageParser();
            PackageParser.Package pkg = parser.parsePackage(apk, flags);
            RefUtils.invokeSafely(PackageParser.class, null, "collectCertificates",
                    new Class[]{PackageParser.Package.class, int.class}, pkg, flags);
            return pkg;
        }

    }

    private static final class PackageParserV21 {
        static final PackageParser.Package parsePackage(Context context, File apk, int flags)
                throws PackageParser.PackageParserException {
            PackageParser parser = new PackageParser();
            PackageParser.Package pkg = parser.parsePackage(apk, flags);
            try {
                parser.collectCertificates(pkg, flags);
            } catch (Throwable e) {

            }
            return pkg;
        }
    }

    private static final class PackageParserLegacy {
        static final PackageParser.Package parsePackage(Context context, File apk, int flags) throws PackageParser.PackageParserException {

            return null;
        }
    }

}

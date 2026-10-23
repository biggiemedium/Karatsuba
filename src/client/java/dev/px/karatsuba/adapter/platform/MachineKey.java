package dev.px.karatsuba.adapter.platform;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import dev.px.core.Core;
import dev.px.core.config.crypto.AesGcmCipher;
import dev.px.core.config.crypto.ConfigCipher;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * The key Core encrypts private config sections with, kept so that only this
 * computer's user can use it.
 *
 * <pre>{@code
 * Core.config().encrypt("accounts", MachineKey.cipher());
 * }</pre>
 *
 * <p>A random 256-bit AES key, made on first use and kept in
 * {@code .minecraft/karatsuba/machine.key} &mdash; beside {@code configs/}, not in
 * it, so a configs folder zipped up for a friend or a bug report does not carry
 * its own key.
 *
 * <h2>What protects the key</h2>
 * On Windows the key is sealed with DPAPI, which ties it to the Windows account
 * that wrote it: another user on the machine, or the file copied to another
 * computer, cannot open it. That is the protection worth having for a refresh
 * token, and it costs the player nothing: no passphrase, no prompt. The JNA
 * bindings it needs ship with the game.
 *
 * <p>Elsewhere the key is written as it is, readable only by its owner where
 * the file system has POSIX permissions. That stops the encrypted file being
 * read by accident, pasted or synced, and no more: anything that can read the
 * key file can read the config. A keychain on macOS and Linux would close that,
 * and this class is where it would go.
 *
 * <h2>Losing the key</h2>
 * A key that cannot be opened &mdash; a Windows reinstall, a different user, a
 * damaged file &mdash; is kept aside and replaced. Core then cannot decrypt what
 * the old key wrote, keeps that file aside too, and starts the section from its
 * defaults: the saved accounts are gone and have to be added again. Nothing is
 * overwritten, so restoring the old key file brings them back.
 *
 * <p>File format: one byte saying how the rest is sealed ({@code D} for DPAPI,
 * {@code R} for raw), then the sealed key.
 */
public final class MachineKey {

    private static final String FILE_NAME = "machine.key";
    private static final byte DPAPI = 'D';
    private static final byte RAW = 'R';
    private static final int KEY_BYTES = 32;

    private MachineKey() {
    }

    /**
     * Loads the key, or makes one, and returns a cipher over it.
     *
     * <p>Call before {@code Core.start()}, which loads the config; it needs only
     * the platform's data directory, which is known once Core is built.
     */
    public static ConfigCipher cipher() {
        Path file = Core.platform().getDataDirectory().toPath().resolve(FILE_NAME);
        byte[] key = load(file);
        try {
            return AesGcmCipher.of(key);
        } finally {
            // AesGcmCipher keeps its own copy.
            Arrays.fill(key, (byte) 0);
        }
    }

    private static byte[] load(Path file) {
        if (Files.exists(file)) {
            try {
                return open(Files.readAllBytes(file));
            } catch (IOException | RuntimeException | LinkageError unreadable) {
                Path kept = file.resolveSibling(FILE_NAME + ".unreadable-" + System.currentTimeMillis());
                Core.log().error("Could not open " + file + "; encrypted config written with it cannot be read."
                        + " It was kept as " + kept + " and a new key made", unreadable);
                try {
                    Files.move(file, kept);
                } catch (IOException moveFailed) {
                    Core.log().error("Could not keep the old key aside", moveFailed);
                }
            }
        }
        return create(file);
    }

    private static byte[] open(byte[] stored) throws IOException {
        if (stored.length < 2) {
            throw new IOException("the key file is empty");
        }
        byte[] sealed = Arrays.copyOfRange(stored, 1, stored.length);
        byte[] key = switch (stored[0]) {
            case DPAPI -> Crypt32Util.cryptUnprotectData(sealed, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN);
            case RAW -> sealed;
            default -> throw new IOException("the key file is in an unknown format");
        };
        if (key.length != KEY_BYTES) {
            throw new IOException("the key file holds " + key.length + " bytes, not " + KEY_BYTES);
        }
        return key;
    }

    private static byte[] create(Path file) {
        byte[] key = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(key);
        try {
            Files.createDirectories(file.getParent());
            write(file, seal(key));
        } catch (IOException failure) {
            // The key still works for this session; it is only lost at exit, and
            // with it whatever was saved under it.
            Core.log().error("Could not save " + file + "; encrypted config will not be readable next launch",
                    failure);
        }
        return key;
    }

    /** @return the key as it goes to disk, sealed with DPAPI where there is one */
    private static byte[] seal(byte[] key) {
        if (com.sun.jna.Platform.isWindows()) {
            try {
                return withFormat(DPAPI, Crypt32Util.cryptProtectData(key, WinCrypt.CRYPTPROTECT_UI_FORBIDDEN));
            } catch (RuntimeException | LinkageError unavailable) {
                Core.log().warn("Windows could not protect the config key; it is kept unprotected instead");
            }
        }
        return withFormat(RAW, key);
    }

    private static byte[] withFormat(byte format, byte[] sealed) {
        byte[] out = new byte[sealed.length + 1];
        out[0] = format;
        System.arraycopy(sealed, 0, out, 1, sealed.length);
        return out;
    }

    /** Written beside the target and moved over it, so a crash never leaves half a key. */
    private static void write(Path file, byte[] bytes) throws IOException {
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException notPosix) {
            // Windows: DPAPI is the protection there, not the file's permissions.
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}

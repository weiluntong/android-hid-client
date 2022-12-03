package me.arianb.usb_hid_client;

import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class KeySender implements Runnable {
	private static Queue<Byte> modQueue;
	private static Queue<Byte> keyQueue;

	private static final ReentrantLock queueLock = new ReentrantLock(true);
	private static final Condition queueNotEmptyCondition = queueLock.newCondition();

	public KeySender() {
		modQueue = new LinkedList<>();
		keyQueue = new LinkedList<>();
	}

	@Override
	public void run() {
		Timber.d("keySender thread started");
		while (!Thread.interrupted()) {
			queueLock.lock();
			// Wait for the queue(s) to actually contain keys
			if (keyQueue.isEmpty()) {
				//Timber.d("Waiting for queue to not be empty.");
				try {
					queueNotEmptyCondition.await();
				} catch (InterruptedException e) {
					Timber.e(Log.getStackTraceString(e));
					queueLock.unlock();
				}
			}
			sendKey(modQueue.remove(), keyQueue.remove());
			//Timber.d("sending key");
			queueLock.unlock();
		}
	}

	public void addKey(byte modifier, byte key) {
		//Timber.d("trying to lock");
		queueLock.lock();
		modQueue.add(modifier);
		keyQueue.add(key);
		queueNotEmptyCondition.signal();
		queueLock.unlock();
		//Timber.d("unlocked");
	}

	public void sendKey(byte modifier, byte key) {
		// Send key
		writeHIDReport(CharacterDevice.KEYBOARD_DEVICE_PATH, modifier, key);

		// Release key
		writeHIDReport(CharacterDevice.KEYBOARD_DEVICE_PATH, (byte) 0, (byte) 0);
	}

	// Writes HID report to character device
	// The modifier is first 2 bytes, the key gets the remaining 6 bytes (but they only ever use 1 byte each)
	private void writeHIDReport(String device, byte modifier, byte key) {
		Timber.d("hid report: %s - %s", modifier, key);

		byte[] report = new byte[]{ modifier, (byte) 0, key, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0 };

		try (FileOutputStream outputStream = new FileOutputStream(device)) {
			outputStream.write(report);
		} catch (IOException e) {
			String stacktrace = Log.getStackTraceString(e);
			if (stacktrace.toLowerCase().contains("errno 108")) {
				MainActivity.makeSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the usb cable", Snackbar.LENGTH_LONG);
			} else if (stacktrace.toLowerCase().contains("permission denied")) {
				if (device.equals(CharacterDevice.KEYBOARD_DEVICE_PATH)) {
					MainActivity.makeFixKeyboardPermissionsSnackbar();
				} else if (device.equals(CharacterDevice.MOUSE_DEVICE_PATH)) {
					MainActivity.makeFixMousePermissionsSnackbar();
				} else {
					Timber.e("ERROR: permission denied and writeHIDReport called with invalid device path");
				}
			} else {
				MainActivity.makeSnackbar("ERROR: Failed to send key.", Snackbar.LENGTH_SHORT);
			}
			Timber.e(stacktrace);
		}
	}
}
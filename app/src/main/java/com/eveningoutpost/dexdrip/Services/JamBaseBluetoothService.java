package com.eveningoutpost.dexdrip.Services;

import android.app.Service;
import android.bluetooth.BluetoothGatt;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.NonNull;

import com.eveningoutpost.dexdrip.Models.JoH;
import com.eveningoutpost.dexdrip.Models.UserError;
import com.eveningoutpost.dexdrip.UtilityModels.Pref;
import com.eveningoutpost.dexdrip.utils.bt.HandleBleScanException;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleCustomOperation;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.connection.RxBleGattCallback;

import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;


// jamorham base class for reactive bluetooth services

public abstract class JamBaseBluetoothService extends Service {

    private final PowerManager.WakeLock wl = JoH.getWakeLock("jam-bluetooth-generic", 1000);
    protected static final String BUGGY_SAMSUNG_ENABLED = "buggy-samsung-enabled";
    protected String TAG = this.getClass().getSimpleName();
    private volatile boolean background_launch_waiting = false;

    protected String handleBleScanException(BleScanException bleScanException) {
        return HandleBleScanException.handle(TAG, bleScanException);
    }

    public void background_automata() {
        background_automata(100);
    }

    public synchronized void background_automata(final int timeout) {
        if (background_launch_waiting) {
            UserError.Log.d(TAG, "Blocked by existing background automata pending");
            return;
        }
        final PowerManager.WakeLock wl = JoH.getWakeLock(TAG + "-background", timeout + 1000);
        background_launch_waiting = true;
        new Thread(() -> {
            JoH.threadSleep(timeout);
            background_launch_waiting = false;
            automata();
            JoH.releaseWakeLock(wl);
        }).start();
    }

    protected synchronized void automata() {
        throw new RuntimeException("automata stub");
    }

    protected synchronized void extendWakeLock(long ms) {
        JoH.releaseWakeLock(wl); // lets not get too messy
        wl.acquire(ms);
    }

    protected void releaseWakeLock() {
        JoH.releaseWakeLock(wl);
    }


    protected class OperationSuccess extends RuntimeException {
        public OperationSuccess(String message) {
            super(message);
            UserError.Log.d(TAG, "Operation Success: " + message);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public void tryGattRefresh(RxBleConnection connection) {
        if (connection == null) return;
        if (JoH.ratelimit("gatt-refresh", 60)) {
            if (Pref.getBoolean("use_gatt_refresh", true)) {
                try {
                    if (connection != null)
                        UserError.Log.d(TAG, "Trying gatt refresh queue");
                    connection.queue((new GattRefreshOperation(0))).timeout(2, TimeUnit.SECONDS).subscribe(
                            readValue -> {
                                UserError.Log.d(TAG, "Refresh OK: " + readValue);
                            }, throwable -> {
                                UserError.Log.d(TAG, "Refresh exception: " + throwable);
                            });
                } catch (NullPointerException e) {
                    UserError.Log.d(TAG, "Probably harmless gatt refresh exception: " + e);
                } catch (Exception e) {
                    UserError.Log.d(TAG, "Got exception trying gatt refresh: " + e);
                }
            } else {
                UserError.Log.d(TAG, "Gatt refresh rate limited");
            }
        }
    }

    private static class GattRefreshOperation implements RxBleCustomOperation<Void> {
        private long delay_ms = 500;

        GattRefreshOperation() {
        }

        GattRefreshOperation(long delay_ms) {
            this.delay_ms = delay_ms;
        }

        @NonNull
        @Override
        public Observable<Void> asObservable(BluetoothGatt bluetoothGatt,
                                             RxBleGattCallback rxBleGattCallback,
                                             Scheduler scheduler) throws Throwable {

            return Observable.fromCallable(() -> refreshDeviceCache(bluetoothGatt))
                    .delay(delay_ms, TimeUnit.MILLISECONDS, Schedulers.computation())
                    .subscribeOn(scheduler);
        }

        private Void refreshDeviceCache(final BluetoothGatt gatt) {
            UserError.Log.d("BaseBluetooth", "Gatt Refresh " + (JoH.refreshDeviceCache("BaseBluetooth", gatt) ? "succeeded" : "failed"));
            return null;
        }
    }

    protected static byte[] nn(final byte[] array) {
        if (array == null) {
            if (JoH.ratelimit("never-null", 60)) {
                UserError.Log.wtf("NeverNull", "Attempt to pass null!!! " + JoH.backTrace());
                return new byte[1];
            }
        }
        return array;
    }

}
/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.services.telephony;

import android.net.Uri;
import android.text.TextUtils;

import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.Phone;
import com.android.services.telecomm.Connection;
import com.android.services.telecomm.ConnectionRequest;
import com.android.services.telecomm.ConnectionService;
import com.android.services.telecomm.Response;
import com.android.services.telecomm.Subscription;

import com.google.android.collect.Sets;

import java.util.Set;

/**
 * The parent class for telephony-based call services. Subclasses provide the specific phone (GSM,
 * CDMA, etc...) to use.
 */
public abstract class TelephonyConnectionService extends ConnectionService {
    private static final Set<com.android.internal.telephony.Connection> sKnownConnections
            = Sets.newHashSet();

    /** {@inheritDoc} */
    @Override
    public void onFindSubscriptions(
            Uri handle,
            Response<Uri, Subscription> response) {
        try {
            responseResult(handle, response, canCall(handle) ? new Subscription() : null);
        } catch (Exception e) {
            responseError(handle, response, "onFindSubscriptions error: " + e.toString());
        }
    }

    /**
     * Initiates the underlying Telephony call, then creates a {@link TelephonyConnection}
     * by calling
     * {@link #createTelephonyConnection(ConnectionRequest,
     *         com.android.internal.telephony.Connection)}
     * at the appropriate time. Should be called by the subclass.
     */
    protected void startCallWithPhone(
            Phone phone,
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> response) {
        Log.d(this, "startCallWithPhone: %s.", request);

        if (phone == null) {
            responseError(request, response, "Phone is null");
            return;
        }

        if (request.getHandle() == null) {
            responseError(request, response, "Handle is null");
            return;
        }

        String number = request.getHandle().getSchemeSpecificPart();
        if (TextUtils.isEmpty(number)) {
            responseError(request, response, "Unable to parse number");
            return;
        }

        com.android.internal.telephony.Connection connection;
        try {
            connection = phone.dial(number);
        } catch (CallStateException e) {
            Log.e(this, e, "Call to Phone.dial failed with exception");
            responseError(request, response, e.getMessage());
            return;
        }

        if (connection == null) {
            responseError(request, response, "Call to phone.dial failed");
            return;
        }

        try {
            responseResult(request, response, createTelephonyConnection(request, connection));
        } catch (Exception e) {
            Log.e(this, e, "Call to createConnection failed with exception");
            responseError(request, response, e.getMessage());
        }
    }

    protected <REQUEST, RESULT> void responseError(
            REQUEST request,
            Response<REQUEST, RESULT> response,
            String reason) {
        Log.d(this, "responseError %s: %s", request, reason);
        response.onError(request, reason);
    }

    protected void responseResult(
            Uri request,
            Response<Uri, Subscription> response,
            Subscription result) {
        Log.d(this, "responseResult %s -> %s", request, result);
        response.onResult(request, result);
    }

    protected void responseResult(
            ConnectionRequest request,
            Response<ConnectionRequest, Connection> response,
            Connection result) {
        Log.d(this, "responseResult %s -> %s", request, result);
        response.onResult(request, result);
    }

    protected final TelephonyConnection createTelephonyConnection(
            ConnectionRequest request,
            final com.android.internal.telephony.Connection connection) {
        final TelephonyConnection telephonyConnection =
                onCreateTelephonyConnection(request, connection);
        sKnownConnections.add(connection);
        telephonyConnection.addConnectionListener(new Connection.ListenerBase() {
            @Override
            public void onDestroyed(Connection c) {
                telephonyConnection.removeConnectionListener(this);
                sKnownConnections.remove(connection);
            }
        });
        return telephonyConnection;
    }

    protected static boolean isConnectionKnown(
            com.android.internal.telephony.Connection connection) {
        return sKnownConnections.contains(connection);
    }

    /**
     * Determine whether this {@link TelephonyConnectionService} can place a call
     * to the supplied handle (phone number).
     *
     * @param handle The proposed handle.
     * @return {@code true} if the handle can be called.
     */
    protected abstract boolean canCall(Uri handle);

    /**
     * Create a Telephony-specific {@link Connection} object.
     *
     * @param request A request for creating a {@link Connection}.
     * @param connection An underlying Telephony {@link com.android.internal.telephony.Connection}
     *         to use.
     * @return A new {@link TelephonyConnection}.
     */
    protected abstract TelephonyConnection onCreateTelephonyConnection(
            ConnectionRequest request,
            com.android.internal.telephony.Connection connection);
}

/**
 * Designed and developed by Aidan Follestad (@afollestad)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused")

package com.afollestad.assent

import androidx.annotation.CheckResult
import androidx.fragment.app.Fragment
import com.afollestad.assent.internal.Assent.Companion.LOCK
import com.afollestad.assent.internal.Assent.Companion.ensureFragment
import com.afollestad.assent.internal.Assent.Companion.get
import com.afollestad.assent.internal.PendingRequest
import com.afollestad.assent.internal.equalsPermissions
import com.afollestad.assent.rationale.RationaleHandler
import timber.log.Timber

@CheckResult fun Fragment.isAllGranted(vararg permissions: Permission) =
  activity?.isAllGranted(*permissions) ?: throw IllegalStateException(
      "Fragment's Activity is null."
  )

fun Fragment.askForPermissions(
  vararg permissions: Permission,
  requestCode: Int = 60,
  rationaleHandler: RationaleHandler? = null,
  callback: Callback
) = synchronized(LOCK) {
  log("askForPermissions(${permissions.joinToString()})")

  if (rationaleHandler != null) {
    rationaleHandler.requestPermissions(permissions, requestCode, callback)
    return
  }

  val currentRequest = get().currentPendingRequest
  if (currentRequest != null &&
      currentRequest.permissions.equalsPermissions(*permissions)
  ) {
    // Request matches permissions, append a callback
    currentRequest.callbacks.add(callback)
    return@askForPermissions
  }

  // Create a new pending request since none exist for these permissions
  val newPendingRequest = PendingRequest(
      permissions = permissions.toList(),
      requestCode = requestCode,
      callbacks = mutableListOf(callback)
  )

  if (currentRequest == null) {
    // There is no active request so we can execute immediately
    get().currentPendingRequest = newPendingRequest
    ensureFragment(this@askForPermissions).perform(newPendingRequest)
  } else {
    // There is an active request, append this new one to the queue
    if (currentRequest.requestCode == requestCode) {
      newPendingRequest.requestCode = requestCode + 1
    }
    get().requestQueue += newPendingRequest
  }
}

fun Fragment.runWithPermissions(
  vararg permissions: Permission,
  requestCode: Int = 80,
  rationaleHandler: RationaleHandler? = null,
  execute: Callback
) {
  log("runWithPermissions($permissions)")
  askForPermissions(
      *permissions,
      requestCode = requestCode,
      rationaleHandler = rationaleHandler
  ) {
    if (it.isAllGranted(*permissions)) {
      execute.invoke(it)
    }
  }
}

private fun Any.log(message: String) {
  Timber.tag("Assent-${name()}")
  Timber.d(message)
}

private fun Any.warn(message: String) {
  Timber.tag("Assent-${name()}")
  Timber.w(message)
}

private fun Any.name() = this::class.java.simpleName

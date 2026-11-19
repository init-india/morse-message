package com.morsemessage.ringtone

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import com.morsemessage.app

/**
 * Runs when the contacts change (new contact, renamed, deleted) so they get, or lose, their
 * Morse ringtone without opening the app. Content-triggered jobs fire once, so it re-arms itself.
 */
class ContactsChangedJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val app = applicationContext.app
        if (!app.settings.current.morseRingtones || !hasContactsPermission(this)) return false
        Thread {
            runCatching { app.ringtones.syncAll(app.settings.current) }
            jobFinished(params, false)
            schedule(applicationContext) // re-arm for the next change
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val JOB_ID = 4_201

        fun schedule(context: Context) {
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, ContactsChangedJob::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        ContactsContract.Contacts.CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    )
                )
                .setTriggerContentUpdateDelay(10_000)   // wait for edits to settle
                .setTriggerContentMaxDelay(120_000)
                .build()
            context.getSystemService(JobScheduler::class.java)?.schedule(info)
        }

        /** Arms the job unless it already is (re-scheduling would reset a pending trigger). */
        fun ensureScheduled(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) == null) schedule(context)
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }

        fun hasContactsPermission(context: Context): Boolean =
            listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
    }
}

/** Content-triggered jobs don't survive a reboot, so re-arm after boot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && context.app.settings.current.morseRingtones) {
            ContactsChangedJob.ensureScheduled(context)
        }
    }
}

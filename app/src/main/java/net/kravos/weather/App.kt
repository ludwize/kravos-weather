package net.kravos.weather

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import net.kravos.weather.data.Http
import net.kravos.weather.data.Prefs
import net.kravos.weather.data.Repository
import net.kravos.weather.work.RefreshWorker
import org.osmdroid.config.Configuration

class App : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        prefs = Prefs(this)
        repo = Repository(this)

        Configuration.getInstance().apply {
            userAgentValue = Http.USER_AGENT
            osmdroidBasePath = filesDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("tiles")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(CH_ALERTS, "Upozorenja na vremenske nepogode", NotificationManager.IMPORTANCE_HIGH)
                        .apply { description = "Zvanična upozorenja (Meteoalarm / RHMZ) i oluje" },
                    NotificationChannel(CH_RAIN, "Kiša uskoro", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Obaveštenje kad padavine počinju u narednih sat vremena" },
                    NotificationChannel(CH_DAILY, "Jutarnji pregled", NotificationManager.IMPORTANCE_LOW)
                        .apply { description = "Kratka prognoza za dan svako jutro" },
                    NotificationChannel(CH_HEALTH, "Vazduh, polen i mraz", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Loš kvalitet vazduha, visok polen, mraz" },
                ),
            )
        }
        RefreshWorker.schedule(this)
    }

    companion object {
        const val CH_ALERTS = "alerts"
        const val CH_RAIN = "rain"
        const val CH_DAILY = "daily"
        const val CH_HEALTH = "health"
    }
}

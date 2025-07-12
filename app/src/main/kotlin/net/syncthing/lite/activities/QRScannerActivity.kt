package net.syncthing.lite.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import net.syncthing.lite.R

class QRScannerActivity : AppCompatActivity() {
    
    private lateinit var captureManager: CaptureManager
    private lateinit var barcodeScannerView: DecoratedBarcodeView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scanner)
        
        barcodeScannerView = findViewById(R.id.zxing_barcode_scanner)
        
        // Set up the barcode callback
        barcodeScannerView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                if (result != null) {
                    // Return the scanned result
                    val returnIntent = Intent()
                    returnIntent.putExtra(SCAN_RESULT, result.text)
                    setResult(RESULT_OK, returnIntent)
                    finish()
                }
            }
        })
        
        captureManager = CaptureManager(this, barcodeScannerView)
        captureManager.initializeFromIntent(intent, savedInstanceState)
        captureManager.decode()
    }
    
    override fun onResume() {
        super.onResume()
        captureManager.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        captureManager.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        captureManager.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        captureManager.onSaveInstanceState(outState)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        captureManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    companion object {
        const val SCAN_RESULT = "SCAN_RESULT"
    }
}
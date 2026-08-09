package br.com.brew.brassia.sensor.domain;

/** Dispositivo pausado ou revogado não aceita leitura (INT-001). */
public class InactiveDeviceException extends RuntimeException {

    private final String code;
    private final DeviceStatus status;

    public InactiveDeviceException(String code, DeviceStatus status) {
        super("dispositivo " + code + " está em " + status);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public DeviceStatus status() {
        return status;
    }
}

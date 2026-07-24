package br.com.brew.brassia.referencedata.domain;

/** Versão do dataset conforme declarada pela fonte (ex.: "2021", "2026.1"). */
public record DatasetVersion(String value) {
    public DatasetVersion {
        value = value == null ? "" : value.trim();
        if (value.isBlank() || value.length() > 60) {
            throw new IllegalArgumentException("versão do dataset deve conter de 1 a 60 caracteres");
        }
    }
}

package foo;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

public class SignaturesPetition {
    public static final int MAX_SIGNATURES = 1000; // Replace with the actual max value you want
    private Long petitionId;
    private List<String> signatures;
    private boolean isFull;
    private Date createdAt;

    public SignaturesPetition() {
        this.signatures = new ArrayList<>();
        this.isFull = false;
        this.createdAt = new Date();
    }

    public Long getPetitionId() {
        return petitionId;
    }

    public void setPetitionId(Long petitionId) {
        this.petitionId = petitionId;
    }

    public List<String> getSignatures() {
        return signatures;
    }

    public void setSignatures(List<String> signatures) {
        this.signatures = signatures;
    }

    public boolean getIsFull() {
        return isFull;
    }

    public void setIsFull(boolean isFull) {
        this.isFull = isFull;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}

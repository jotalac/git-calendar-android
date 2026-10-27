package gcal

import (
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

func TestNewCoreUsesDataDir(t *testing.T) {
	dir := t.TempDir()
	core, err := NewCore(dir)
	if err != nil {
		t.Fatalf("NewCore: %v", err)
	}
	if err := core.CreateCalendar("default", ""); err != nil {
		t.Fatalf("CreateCalendar: %v", err)
	}
	if _, err := os.Stat(filepath.Join(dir, ".git-calendar-data", "default")); err != nil {
		t.Fatalf("calendar not stored under data dir: %v", err)
	}
}

func TestErrorsAreJSON(t *testing.T) {
	core, err := NewCore(t.TempDir())
	if err != nil {
		t.Fatalf("NewCore: %v", err)
	}
	_, err = core.CreateEvent(`{"title":"","calendar":"missing"}`)
	if err == nil {
		t.Fatal("expected an error")
	}
	var decoded coreError
	if jsonErr := json.Unmarshal([]byte(err.Error()), &decoded); jsonErr != nil {
		t.Fatalf("error is not JSON: %q", err.Error())
	}
	if decoded.Message == "" {
		t.Fatalf("missing message: %+v", decoded)
	}
}

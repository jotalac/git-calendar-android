// Package gcal adapts github.com/git-calendar/core/pkg/api for Android.
//
// It roots the core's data directory inside the app sandbox (Android has no
// $HOME, so the core would otherwise fall back to /sdcard and panic) and
// encodes errors as JSON so error codes and calendar names survive gomobile,
// which only transports an error's message.
package gcal

import (
	"fmt"
	"os"
	_ "time/tzdata" // DTSTART;TZID and iCalendar zones need a zone database

	"github.com/git-calendar/core/pkg/api"
)

// Core is the Android-facing wrapper around api.Api.
type Core struct {
	inner *api.Api
}

// NewCore creates a core that stores its data under dataDir/.git-calendar-data.
func NewCore(dataDir string) (core *Core, err error) {
	if dataDir == "" {
		return nil, wrapErr(fmt.Errorf("data directory cannot be empty"))
	}
	if err := os.Setenv("HOME", dataDir); err != nil {
		return nil, wrapErr(err)
	}
	defer func() {
		if r := recover(); r != nil {
			core = nil
			err = wrapErr(fmt.Errorf("failed to initialize core: %v", r))
		}
	}()
	return &Core{inner: api.NewApi()}, nil
}

// ----------------------------------------------- Calendars ------------------------------------------------

func (c *Core) CreateCalendar(name, password string) error {
	return wrapErr(c.inner.CreateCalendar(name, password))
}

func (c *Core) RemoveCalendar(name string) error { return wrapErr(c.inner.RemoveCalendar(name)) }

func (c *Core) RenameCalendar(oldName, newName string) error {
	return wrapErr(c.inner.RenameCalendar(oldName, newName))
}

func (c *Core) LoadCalendars() error { return wrapErr(c.inner.LoadCalendars()) }

// SetTimeZone sets the IANA zone for date-only iCalendar events; Go's local zone is always UTC on Android.
func (c *Core) SetTimeZone(name string) error { return wrapErr(c.inner.SetTimeZone(name)) }

func (c *Core) ListCalendars() (string, error) { return wrapStr(c.inner.ListCalendars()) }

func (c *Core) UpdateRemote(calendar, remoteURL string, readonly bool) error {
	return wrapErr(c.inner.UpdateRemote(calendar, remoteURL, readonly))
}

func (c *Core) CloneCalendar(repoURL, password string, readonly bool) error {
	return wrapErr(c.inner.CloneCalendar(repoURL, password, readonly))
}

func (c *Core) SyncAll() error { return wrapErr(c.inner.SyncAll()) }

// ----------------------------------------------- iCalendar ------------------------------------------------

func (c *Core) ImportICalURL(name, rawURL string) error {
	return wrapErr(c.inner.ImportICalURL(name, rawURL))
}

func (c *Core) UpdateICalURL(name, rawURL string) error {
	return wrapErr(c.inner.UpdateICalURL(name, rawURL))
}

func (c *Core) ImportICalFile(calendar, tagID, data string) error {
	return wrapErr(c.inner.ImportICalFile(calendar, tagID, data))
}

func (c *Core) ExportICal(calendar string) ([]byte, error) {
	return wrapBytes(c.inner.ExportICal(calendar))
}

// ----------------------------------------------- Backups --------------------------------------------------

func (c *Core) ExportZip(calendar string) ([]byte, error) {
	return wrapBytes(c.inner.ExportZip(calendar))
}

func (c *Core) RestoreZip(data []byte) error { return wrapErr(c.inner.RestoreZip(data)) }

// ------------------------------------------------ Events --------------------------------------------------

func (c *Core) CreateEvent(eventJSON string) (string, error) {
	return wrapStr(c.inner.CreateEvent(eventJSON))
}

func (c *Core) UpdateEvent(eventJSON string) (string, error) {
	return wrapStr(c.inner.UpdateEvent(eventJSON))
}

func (c *Core) UpdateRepeatingEvent(oldEventJSON, newEventJSON string, strategy int) (string, error) {
	return wrapStr(c.inner.UpdateRepeatingEvent(oldEventJSON, newEventJSON, strategy))
}

func (c *Core) RemoveEvent(eventJSON string) error { return wrapErr(c.inner.RemoveEvent(eventJSON)) }

func (c *Core) RemoveRepeatingEvent(eventJSON string, strategy int) error {
	return wrapErr(c.inner.RemoveRepeatingEvent(eventJSON, strategy))
}

func (c *Core) GetEvent(id string) (string, error) { return wrapStr(c.inner.GetEvent(id)) }

func (c *Core) GetEvents(from, to, filterJSON string) (string, error) {
	return wrapStr(c.inner.GetEvents(from, to, filterJSON))
}

func (c *Core) GetNotifications(from, to, filterJSON string) (string, error) {
	return wrapStr(c.inner.GetNotifications(from, to, filterJSON))
}

// ------------------------------------------------- Tags ---------------------------------------------------

func (c *Core) CreateTag(calendar, tagJSON string) (string, error) {
	return wrapStr(c.inner.CreateTag(calendar, tagJSON))
}

func (c *Core) UpdateTag(calendar, tagJSON string) (string, error) {
	return wrapStr(c.inner.UpdateTag(calendar, tagJSON))
}

func (c *Core) RemoveTag(calendar, id string) error { return wrapErr(c.inner.RemoveTag(calendar, id)) }

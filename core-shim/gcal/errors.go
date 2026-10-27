package gcal

import (
	"encoding/json"
	"errors"

	"github.com/git-calendar/core/pkg/errcode"
)

// coreError is the JSON shape carried in an error's message across gomobile.
type coreError struct {
	Code     string `json:"code,omitempty"`
	Calendar string `json:"calendar,omitempty"`
	Message  string `json:"message"`
}

// wrapErr replaces err with one whose message is a JSON coreError. It returns nil when err is nil.
func wrapErr(err error) error {
	if err == nil {
		return nil
	}
	data := coreError{Message: err.Error()}
	if code, ok := errcode.CodeOf(err); ok {
		data.Code = string(code)
	}
	if calendar, ok := errcode.CalendarOf(err); ok {
		data.Calendar = calendar
	}
	encoded, marshalErr := json.Marshal(data)
	if marshalErr != nil {
		return err
	}
	return errors.New(string(encoded))
}

func wrapStr(value string, err error) (string, error) { return value, wrapErr(err) }

func wrapBytes(value []byte, err error) ([]byte, error) { return value, wrapErr(err) }

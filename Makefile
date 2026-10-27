GO ?= go
ANDROID_HOME ?= $(HOME)/Library/Android/sdk
ANDROID_NDK_HOME ?= $(lastword $(sort $(wildcard $(ANDROID_HOME)/ndk/*)))
ANDROID_API ?= 31
AAR := app/libs/gcal.aar
TOOLS_DIR := $(abspath build/go-tools)

.PHONY: core core-test clean-core

# Build the gomobile AAR wrapping git-calendar/core (see core-shim/gcal).
core: $(AAR)

$(AAR): $(shell find core-shim core/pkg core/go.mod -type f)
	mkdir -p app/libs $(TOOLS_DIR)
	cd core-shim && $(GO) build -o $(TOOLS_DIR)/gobind golang.org/x/mobile/cmd/gobind
	cd core-shim && PATH="$(TOOLS_DIR):$$PATH" ANDROID_HOME="$(ANDROID_HOME)" ANDROID_NDK_HOME="$(ANDROID_NDK_HOME)" \
		$(GO) tool gomobile bind -target=android/arm64,android/amd64 -androidapi $(ANDROID_API) \
		-javapkg dev.jotalac.core -ldflags="-s -w" -o ../$(AAR) ./gcal
	rm -f app/libs/gcal-sources.jar

core-test:
	cd core-shim && $(GO) vet ./... && $(GO) test ./...

clean-core:
	rm -f $(AAR)

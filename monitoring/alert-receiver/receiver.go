package main

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

type event struct {
	Name   string `json:"name"`
	Status string `json:"status"`
}

var mu sync.Mutex
var events = make([]event, 0, 128)

func main() {
	http.HandleFunc("/alerts", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodPost {
			http.Error(w, "method", http.StatusMethodNotAllowed)
			return
		}
		defer r.Body.Close()
		var payload struct {
			Alerts []struct {
				Status string            `json:"status"`
				Labels map[string]string `json:"labels"`
			} `json:"alerts"`
		}
		if err := json.NewDecoder(io.LimitReader(r.Body, 131073)).Decode(&payload); err != nil {
			http.Error(w, "invalid", http.StatusBadRequest)
			return
		}
		mu.Lock()
		for _, a := range payload.Alerts {
			events = append(events, event{a.Labels["alertname"], a.Status})
		}
		if len(events) > 128 {
			events = append([]event(nil), events[len(events)-128:]...)
		}
		mu.Unlock()
		w.WriteHeader(http.StatusOK)
	})
	http.HandleFunc("/received", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			http.Error(w, "method", http.StatusMethodNotAllowed)
			return
		}
		mu.Lock()
		defer mu.Unlock()
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(events)
	})
	server := &http.Server{Addr: ":8080", ReadHeaderTimeout: 3 * time.Second,
		ReadTimeout: 10 * time.Second, WriteTimeout: 10 * time.Second}
	log.Fatal(server.ListenAndServe())
}

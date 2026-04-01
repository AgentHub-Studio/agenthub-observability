package database

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/ClickHouse/clickhouse-go/v2"
	"github.com/google/uuid"
)

// AggregationPeriod defines the time granularity for metric aggregations.
type AggregationPeriod string

const (
	PeriodHour  AggregationPeriod = "HOUR"
	PeriodDay   AggregationPeriod = "DAY"
	PeriodWeek  AggregationPeriod = "WEEK"
	PeriodMonth AggregationPeriod = "MONTH"
)

// AggregationType defines the aggregation function applied to metric values.
type AggregationType string

const (
	AggSum   AggregationType = "SUM"
	AggAvg   AggregationType = "AVG"
	AggMin   AggregationType = "MIN"
	AggMax   AggregationType = "MAX"
	AggCount AggregationType = "COUNT"
	AggP50   AggregationType = "P50"
	AggP95   AggregationType = "P95"
	AggP99   AggregationType = "P99"
)

// Aggregator computes and persists aggregated metrics from raw metric_events.
type Aggregator struct {
	conn     clickhouse.Conn
	interval time.Duration
}

// NewAggregator creates an Aggregator that runs every interval.
func NewAggregator(conn clickhouse.Conn, interval time.Duration) *Aggregator {
	return &Aggregator{conn: conn, interval: interval}
}

// Run starts the periodic aggregation loop. Blocks until ctx is cancelled.
func (a *Aggregator) Run(ctx context.Context) {
	ticker := time.NewTicker(a.interval)
	defer ticker.Stop()
	for {
		select {
		case <-ticker.C:
			if err := a.aggregate(ctx); err != nil {
				slog.Error("aggregator: cycle failed", "err", err)
			}
		case <-ctx.Done():
			return
		}
	}
}

// aggregate computes all aggregation combinations for the last two periods
// and upserts them into aggregated_metrics.
func (a *Aggregator) aggregate(ctx context.Context) error {
	now := time.Now().UTC()

	type aggSpec struct {
		aggType AggregationType
		fn      string
	}
	specs := []aggSpec{
		{AggSum, "sum(value)"},
		{AggAvg, "avg(value)"},
		{AggMin, "min(value)"},
		{AggMax, "max(value)"},
		{AggCount, "count()"},
		{AggP50, "quantile(0.5)(value)"},
		{AggP95, "quantile(0.95)(value)"},
		{AggP99, "quantile(0.99)(value)"},
	}

	periods := []struct {
		period    AggregationPeriod
		trunc     string
		lookback  time.Duration
	}{
		{PeriodHour, "toStartOfHour(occurred_at)", 2 * time.Hour},
		{PeriodDay, "toStartOfDay(occurred_at)", 48 * time.Hour},
	}

	for _, p := range periods {
		since := now.Add(-p.lookback)
		for _, s := range specs {
			if err := a.computeAndInsert(ctx, s.fn, string(s.aggType), string(p.period), p.trunc, since, now); err != nil {
				return fmt.Errorf("aggregator: %s/%s: %w", s.aggType, p.period, err)
			}
		}
	}
	return nil
}

func (a *Aggregator) computeAndInsert(
	ctx context.Context,
	aggFn, aggType, aggPeriod, truncExpr string,
	since, until time.Time,
) error {
	query := fmt.Sprintf(
		`SELECT tenant_id, metric_name, %s AS period_start, %s AS agg_value, count() AS sample_count
		 FROM metric_events
		 WHERE occurred_at >= ? AND occurred_at < ?
		 GROUP BY tenant_id, metric_name, period_start`,
		truncExpr, aggFn,
	)

	rows, err := a.conn.Query(ctx, query, since, until)
	if err != nil {
		return fmt.Errorf("query: %w", err)
	}
	defer rows.Close()

	type row struct {
		TenantID    string
		MetricName  string
		PeriodStart time.Time
		Value       float64
		SampleCount int64
	}

	var results []row
	for rows.Next() {
		var r row
		if err := rows.Scan(&r.TenantID, &r.MetricName, &r.PeriodStart, &r.Value, &r.SampleCount); err != nil {
			return fmt.Errorf("scan: %w", err)
		}
		results = append(results, r)
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("rows: %w", err)
	}
	if len(results) == 0 {
		return nil
	}

	batch, err := a.conn.PrepareBatch(ctx, "INSERT INTO aggregated_metrics")
	if err != nil {
		return fmt.Errorf("prepare batch: %w", err)
	}

	now := time.Now().UTC()
	for _, r := range results {
		periodEnd := periodEndTime(r.PeriodStart, aggPeriod)
		if err := batch.Append(
			uuid.New().String(),
			r.TenantID,
			r.MetricName,
			aggType,
			aggPeriod,
			r.PeriodStart,
			periodEnd,
			r.Value,
			r.SampleCount,
			now,
		); err != nil {
			return fmt.Errorf("append: %w", err)
		}
	}

	return batch.Send()
}

func periodEndTime(start time.Time, period string) time.Time {
	switch period {
	case string(PeriodHour):
		return start.Add(time.Hour)
	case string(PeriodDay):
		return start.Add(24 * time.Hour)
	case string(PeriodWeek):
		return start.Add(7 * 24 * time.Hour)
	case string(PeriodMonth):
		return start.AddDate(0, 1, 0)
	default:
		return start.Add(time.Hour)
	}
}

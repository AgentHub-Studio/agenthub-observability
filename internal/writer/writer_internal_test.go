package writer

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestNodeCountToInt32SaturatesOverflow(t *testing.T) {
	assert.Equal(t, int32(maxInt32), nodeCountToInt32(maxInt32+1))
	assert.Equal(t, int32(minInt32), nodeCountToInt32(minInt32-1))
	assert.Equal(t, int32(42), nodeCountToInt32(42))
}

// Extend Tablesort.js with a custom parser for comma-separated numbers.
Tablesort.extend('numeric-comma', function(item) {
  return /^[−-]?[\d,]+(?:\.\d+)?$/.test(item.trim());
}, function(a, b) {
  var cleanA = a.trim().replace(/,/g, '').replace('−', '-');
  var cleanB = b.trim().replace(/,/g, '').replace('−', '-');
  var numA = parseFloat(cleanA);
  var numB = parseFloat(cleanB);
  numA = isNaN(numA) ? 0 : numA;
  numB = isNaN(numB) ? 0 : numB;
  return numA - numB;
});

// Extend Tablesort.js with a custom parser for intensity values.
Tablesort.extend('intensity', function(item) {
  return /^(low|medium|moderate|high)$/i.test(item.trim());
}, function(a, b) {
  var intensityMap = {
    'low': 0, 'medium': 1, 'moderate': 1, 'high': 2
  };
  var valueA = intensityMap[a.toLowerCase().trim()];
  var valueB = intensityMap[b.toLowerCase().trim()];
  valueA = (valueA === undefined) ? -1 : valueA;
  valueB = (valueB === undefined) ? -1 : valueB;
  return valueA - valueB;
});

// Run initialization after the page content is loaded.
document.addEventListener('DOMContentLoaded', function() {
  var sortableTables = document.querySelectorAll('table.wikitable.sortable');
  sortableTables.forEach(function(table) {
    // Ensure the table has a THEAD, which is required by the library.
    // This prevents the header row from being sorted along with the data.
    if (!table.querySelector('thead')) {
      var thead = document.createElement('thead');
      if (table.rows.length > 0) {
        thead.appendChild(table.rows[0]);
      }
      table.insertBefore(thead, table.firstChild);
    }
    // Initialize the library on the table.
    new Tablesort(table);
  });
});

sliceLabel = (label, series) ->
  count = Math.round(series.data[0][1])
  "<div style=\"font-size:8pt;text-align:center;padding:2px;color:white;\">" + label + "<br/>" + count + "</div>"

chartOptions = (showLabels) ->
  series:
    pie:
      show: true
      radius: 1
      label:
        show: showLabels
        radius: 3 / 4
        formatter: sliceLabel
        background:
          opacity: 0.5
          color: "#000"
      highlight:
        opacity: 0.2
  grid:
    hoverable: true
    clickable: true
  legend:
    show: false

window.createPieChart = (chartId, counts, urls, showLabels = true) ->
  slices = [
    {
      label: "Pass"
      data: counts.pass
      color: "#609000"
    }
    {
      label: "Warn"
      data: counts.warn
      color: "#FFBF00"
    }
    {
      label: "Fail"
      data: counts.fail
      color: "#b94a48"
    }
  ]
  $.plot ("#" + chartId), slices, chartOptions(showLabels)

  $("#" + chartId).bind "plotclick", (event, pos, obj) ->
    return unless obj
    if obj.series.label is "Pass"
      window.location = urls.pass
    else if obj.series.label is "Warn"
      window.location = urls.warn
    else if obj.series.label is "Fail"
      window.location = urls.fail
